package com.agentmind.chat.memory.repository.redis;

import com.agentmind.chat.memory.config.ChatMemoryProperties;
import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatConversationStatus;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import com.agentmind.chat.memory.repository.ChatMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/**
 * 短期会话记忆的 Redis 适配器。
 *
 * <p>会话列表使用有序集合，会话正文使用字符串，消息正文使用会话级哈希，消息顺序使用有序集合。
 * 该布局兼顾分页查询、整段会话续期和单条消息原子更新，并保持与内存适配器一致的仓储语义。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.chat.memory", name = "store", havingValue = "redis")
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final int RECENT_MESSAGE_SCAN_BATCH_SIZE = 50;

    /**
     * 创建消息与更新会话活跃时间在同一个 Redis 原子操作内完成，避免出现消息已保存但会话列表未刷新的中间状态。
     */
    private static final DefaultRedisScript<Long> CREATE_MESSAGE_SCRIPT = new DefaultRedisScript<>("""
            local conversationJson = redis.call('GET', KEYS[1])
            if not conversationJson then
                return 0
            end

            local conversation = cjson.decode(conversationJson)
            conversation.updatedAt = ARGV[4]

            redis.call('SET', KEYS[1], cjson.encode(conversation))
            redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
            redis.call('ZADD', KEYS[3], ARGV[3], ARGV[1])
            redis.call('ZADD', KEYS[4], ARGV[5], ARGV[6])
            redis.call('EXPIRE', KEYS[1], ARGV[7])
            redis.call('EXPIRE', KEYS[2], ARGV[7])
            redis.call('EXPIRE', KEYS[3], ARGV[7])
            redis.call('EXPIRE', KEYS[4], ARGV[7])
            return 1
            """, Long.class);

    /**
     * 助手消息只能从等待状态迁移一次。多个完成、失败或取消信号并发到达时，只有首个信号能写入最终正文和状态。
     */
    private static final DefaultRedisScript<String> TRANSITION_MESSAGE_SCRIPT = new DefaultRedisScript<>("""
            local conversationJson = redis.call('GET', KEYS[1])
            if not conversationJson then
                return nil
            end

            local existingJson = redis.call('HGET', KEYS[2], ARGV[1])
            if not existingJson then
                return nil
            end

            local existing = cjson.decode(existingJson)
            if existing.status == ARGV[2] then
                redis.call('HSET', KEYS[2], ARGV[1], ARGV[3])
                local conversation = cjson.decode(conversationJson)
                conversation.updatedAt = ARGV[4]
                redis.call('SET', KEYS[1], cjson.encode(conversation))
                redis.call('ZADD', KEYS[4], ARGV[5], ARGV[6])
                existingJson = ARGV[3]
            end

            redis.call('EXPIRE', KEYS[1], ARGV[7])
            redis.call('EXPIRE', KEYS[2], ARGV[7])
            redis.call('EXPIRE', KEYS[3], ARGV[7])
            redis.call('EXPIRE', KEYS[4], ARGV[7])
            return existingJson
            """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisChatMemoryKeyFactory keyFactory;
    private final RedisChatMemoryCodec codec;
    private final Duration ttl;
    private final long ttlSeconds;

    public RedisChatMemoryRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ChatMemoryProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = new RedisChatMemoryKeyFactory(properties);
        this.codec = new RedisChatMemoryCodec(objectMapper, properties);
        this.ttl = requirePositiveTtl(properties.getTtl());
        this.ttlSeconds = Math.max(1L, ttl.toSeconds());
    }

    @Override
    public ChatConversation createConversation(Long workspaceId, String title) {
        Long conversationId = redisTemplate.opsForValue().increment(keyFactory.conversationSequence());
        if (conversationId == null) {
            throw new IllegalStateException("Redis 无法生成会话编号");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ChatConversation conversation = new ChatConversation(
                conversationId,
                workspaceId,
                title,
                ChatConversationStatus.ACTIVE,
                now,
                now
        );
        redisTemplate.opsForValue().set(
                keyFactory.conversation(workspaceId, conversationId),
                codec.writeConversation(conversation),
                ttl
        );
        redisTemplate.opsForZSet().add(
                keyFactory.conversationIndex(workspaceId),
                conversationId.toString(),
                timestampScore(now)
        );
        redisTemplate.expire(keyFactory.conversationIndex(workspaceId), ttl);
        return conversation;
    }

    @Override
    public Optional<ChatConversation> findConversationByWorkspaceIdAndId(Long workspaceId, Long conversationId) {
        String json = redisTemplate.opsForValue().get(keyFactory.conversation(workspaceId, conversationId));
        if (json == null) {
            return Optional.empty();
        }
        ChatConversation conversation = codec.readConversation(json);
        if (!conversation.workspaceId().equals(workspaceId) || !conversation.id().equals(conversationId)) {
            return Optional.empty();
        }
        refreshConversationTtl(workspaceId, conversationId);
        return Optional.of(conversation);
    }

    @Override
    public List<ChatConversation> findConversationsByWorkspaceId(Long workspaceId, int offset, int limit) {
        List<ChatConversation> page = loadExistingConversations(workspaceId).stream()
                .skip(offset)
                .limit(limit)
                .toList();
        page.forEach(conversation -> refreshConversationTtl(workspaceId, conversation.id()));
        return page;
    }

    @Override
    public long countConversationsByWorkspaceId(Long workspaceId) {
        return loadExistingConversations(workspaceId).size();
    }

    @Override
    public ChatMessage createMessage(
            Long workspaceId,
            Long conversationId,
            ChatMessageRole role,
            ChatMessageStatus status,
            String content
    ) {
        requireConversation(workspaceId, conversationId);
        Long messageId = redisTemplate.opsForValue().increment(keyFactory.messageSequence());
        if (messageId == null) {
            throw new IllegalStateException("Redis 无法生成消息编号");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ChatMessage message = new ChatMessage(
                messageId,
                workspaceId,
                conversationId,
                role,
                status,
                content,
                "",
                now,
                now
        );
        Long created = redisTemplate.execute(
                CREATE_MESSAGE_SCRIPT,
                conversationKeys(workspaceId, conversationId),
                messageId.toString(),
                codec.writeMessage(message),
                messageId.toString(),
                now.toString(),
                Double.toString(timestampScore(now)),
                conversationId.toString(),
                Long.toString(ttlSeconds)
        );
        if (created == null || created == 0L) {
            throw new IllegalStateException("会话不存在或不属于当前知识空间");
        }
        return message;
    }

    @Override
    public Optional<ChatMessage> findMessageByWorkspaceIdAndConversationIdAndId(
            Long workspaceId,
            Long conversationId,
            Long messageId
    ) {
        Object value = redisTemplate.opsForHash().get(
                keyFactory.messageHash(workspaceId, conversationId),
                messageId.toString()
        );
        if (value == null) {
            return Optional.empty();
        }
        ChatMessage message = codec.readMessage(value.toString());
        if (!belongsTo(message, workspaceId, conversationId)) {
            return Optional.empty();
        }
        refreshConversationTtl(workspaceId, conversationId);
        return Optional.of(message);
    }

    @Override
    public Optional<ChatMessage> transitionPendingMessage(
            Long workspaceId,
            Long conversationId,
            Long messageId,
            ChatMessageStatus targetStatus,
            String content,
            String failureReason
    ) {
        Optional<ChatMessage> existingOptional = findMessageByWorkspaceIdAndConversationIdAndId(
                workspaceId,
                conversationId,
                messageId
        );
        if (existingOptional.isEmpty()) {
            return Optional.empty();
        }

        ChatMessage existing = existingOptional.get();
        OffsetDateTime now = OffsetDateTime.now();
        ChatMessage target = new ChatMessage(
                existing.id(),
                existing.workspaceId(),
                existing.conversationId(),
                existing.role(),
                targetStatus,
                content,
                failureReason,
                existing.createdAt(),
                now
        );
        String transitionedJson = redisTemplate.execute(
                TRANSITION_MESSAGE_SCRIPT,
                conversationKeys(workspaceId, conversationId),
                messageId.toString(),
                ChatMessageStatus.PENDING.name(),
                codec.writeMessage(target),
                now.toString(),
                Double.toString(timestampScore(now)),
                conversationId.toString(),
                Long.toString(ttlSeconds)
        );
        if (transitionedJson == null) {
            return Optional.empty();
        }
        ChatMessage transitioned = codec.readMessage(transitionedJson);
        return belongsTo(transitioned, workspaceId, conversationId)
                ? Optional.of(transitioned)
                : Optional.empty();
    }

    @Override
    public List<ChatMessage> findMessagesByWorkspaceIdAndConversationId(
            Long workspaceId,
            Long conversationId,
            int offset,
            int limit
    ) {
        requireConversation(workspaceId, conversationId);
        Set<String> messageIds = redisTemplate.opsForZSet().range(
                keyFactory.messageOrder(workspaceId, conversationId),
                offset,
                (long) offset + limit - 1L
        );
        return readMessages(workspaceId, conversationId, messageIds);
    }

    @Override
    public long countMessagesByWorkspaceIdAndConversationId(Long workspaceId, Long conversationId) {
        requireConversation(workspaceId, conversationId);
        Long count = redisTemplate.opsForZSet().zCard(keyFactory.messageOrder(workspaceId, conversationId));
        return count == null ? 0L : count;
    }

    @Override
    public List<ChatMessage> findRecentCompletedMessages(Long workspaceId, Long conversationId, int limit) {
        requireConversation(workspaceId, conversationId);
        if (limit <= 0) {
            return List.of();
        }

        List<ChatMessage> newestFirst = new ArrayList<>();
        long offset = 0L;
        while (newestFirst.size() < limit) {
            Set<String> messageIds = redisTemplate.opsForZSet().reverseRange(
                    keyFactory.messageOrder(workspaceId, conversationId),
                    offset,
                    offset + RECENT_MESSAGE_SCAN_BATCH_SIZE - 1L
            );
            if (messageIds == null || messageIds.isEmpty()) {
                break;
            }
            readMessages(workspaceId, conversationId, messageIds).stream()
                    .sorted(Comparator.comparing(ChatMessage::id).reversed())
                    .filter(message -> message.status() == ChatMessageStatus.COMPLETED)
                    .limit(limit - newestFirst.size())
                    .forEach(newestFirst::add);
            offset += messageIds.size();
        }

        newestFirst.sort(Comparator.comparing(ChatMessage::createdAt).thenComparing(ChatMessage::id));
        return List.copyOf(newestFirst);
    }

    private List<ChatConversation> loadExistingConversations(Long workspaceId) {
        String indexKey = keyFactory.conversationIndex(workspaceId);
        Set<String> conversationIds = redisTemplate.opsForZSet().reverseRange(indexKey, 0, -1);
        if (conversationIds == null || conversationIds.isEmpty()) {
            return List.of();
        }

        List<ChatConversation> conversations = new ArrayList<>();
        for (String idValue : conversationIds) {
            Long conversationId;
            try {
                conversationId = Long.valueOf(idValue);
            } catch (NumberFormatException exception) {
                redisTemplate.opsForZSet().remove(indexKey, idValue);
                continue;
            }
            String json = redisTemplate.opsForValue().get(keyFactory.conversation(workspaceId, conversationId));
            if (json == null) {
                redisTemplate.opsForZSet().remove(indexKey, idValue);
                continue;
            }
            ChatConversation conversation = codec.readConversation(json);
            if (conversation.workspaceId().equals(workspaceId) && conversation.id().equals(conversationId)) {
                conversations.add(conversation);
            }
        }
        conversations.sort(Comparator.comparing(ChatConversation::updatedAt)
                .reversed()
                .thenComparing(ChatConversation::id, Comparator.reverseOrder()));
        return List.copyOf(conversations);
    }

    private List<ChatMessage> readMessages(
            Long workspaceId,
            Long conversationId,
            Set<String> messageIds
    ) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }
        List<Object> hashFields = messageIds.stream().map(value -> (Object) value).toList();
        List<Object> values = redisTemplate.opsForHash().multiGet(
                keyFactory.messageHash(workspaceId, conversationId),
                hashFields
        );
        if (values == null) {
            return List.of();
        }

        List<ChatMessage> messages = values.stream()
                .filter(value -> value != null)
                .map(value -> codec.readMessage(value.toString()))
                .filter(message -> belongsTo(message, workspaceId, conversationId))
                .sorted(Comparator.comparing(ChatMessage::createdAt).thenComparing(ChatMessage::id))
                .toList();
        refreshConversationTtl(workspaceId, conversationId);
        return messages;
    }

    private List<String> conversationKeys(Long workspaceId, Long conversationId) {
        return List.of(
                keyFactory.conversation(workspaceId, conversationId),
                keyFactory.messageHash(workspaceId, conversationId),
                keyFactory.messageOrder(workspaceId, conversationId),
                keyFactory.conversationIndex(workspaceId)
        );
    }

    private void requireConversation(Long workspaceId, Long conversationId) {
        if (findConversationByWorkspaceIdAndId(workspaceId, conversationId).isEmpty()) {
            throw new IllegalStateException("会话不存在或不属于当前知识空间");
        }
    }

    private void refreshConversationTtl(Long workspaceId, Long conversationId) {
        redisTemplate.expire(keyFactory.conversation(workspaceId, conversationId), ttl);
        redisTemplate.expire(keyFactory.messageHash(workspaceId, conversationId), ttl);
        redisTemplate.expire(keyFactory.messageOrder(workspaceId, conversationId), ttl);
        redisTemplate.expire(keyFactory.conversationIndex(workspaceId), ttl);
    }

    private boolean belongsTo(ChatMessage message, Long workspaceId, Long conversationId) {
        return message.workspaceId().equals(workspaceId) && message.conversationId().equals(conversationId);
    }

    private double timestampScore(OffsetDateTime dateTime) {
        return dateTime.toInstant().toEpochMilli();
    }

    private Duration requirePositiveTtl(Duration configuredTtl) {
        if (configuredTtl == null || configuredTtl.isZero() || configuredTtl.isNegative()) {
            throw new IllegalArgumentException("Redis 会话记忆 TTL 必须大于零");
        }
        return configuredTtl;
    }
}
