package com.agentmind.chat.service;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.agent.confirmation.model.dto.CreatedAgentToolConfirmationResponse;
import com.agentmind.agent.proposal.WriteToolProposalService;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.memory.service.ChatMemoryService;
import com.agentmind.chat.model.RagStreamEventType;
import com.agentmind.chat.model.dto.RagChatRequest;
import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.RagStreamCitationEvent;
import com.agentmind.chat.model.dto.RagStreamCompleteEvent;
import com.agentmind.chat.model.dto.RagStreamDeltaEvent;
import com.agentmind.chat.model.dto.RagStreamErrorEvent;
import com.agentmind.chat.model.dto.RagStreamMetadataEvent;
import com.agentmind.chat.model.dto.RagStreamToolCallEvent;
import com.agentmind.chat.model.dto.RagStreamToolConfirmationRequiredEvent;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 检索增强生成 SSE 流式问答编排服务。
 *
 * <p>该服务负责异步准备检索上下文、发送协议事件、传播断连与超时信号，以及结束 SSE 会话。
 * 模型生成细节仍由流式回答端口实现，控制层只负责接收参数和返回响应载体。</p>
 */
@Service
public class RagStreamingChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagStreamingChatService.class);

    private final RagContextAssemblyService contextAssemblyService;
    private final StreamingAnswerGenerator streamingAnswerGenerator;
    private final AsyncTaskExecutor taskExecutor;
    private final RagAnswerGenerationProperties properties;
    private final ChatMemoryService chatMemoryService;
    private final WriteToolProposalService writeToolProposalService;

    public RagStreamingChatService(
            RagContextAssemblyService contextAssemblyService,
            StreamingAnswerGenerator streamingAnswerGenerator,
            @Qualifier("ragStreamingTaskExecutor") AsyncTaskExecutor taskExecutor,
            RagAnswerGenerationProperties properties,
            ChatMemoryService chatMemoryService,
            WriteToolProposalService writeToolProposalService
    ) {
        this.contextAssemblyService = contextAssemblyService;
        this.streamingAnswerGenerator = streamingAnswerGenerator;
        this.taskExecutor = taskExecutor;
        this.properties = properties;
        this.chatMemoryService = chatMemoryService;
        this.writeToolProposalService = writeToolProposalService;
    }

    public SseEmitter stream(Long ownerUserId, Long workspaceId, RagChatRequest request) {
        long timeoutMillis = Math.max(1, properties.getStreamTimeoutMillis());
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        StreamSessionState sessionState = new StreamSessionState();
        registerLifecycleCallbacks(emitter, sessionState);

        try {
            taskExecutor.execute(() -> executeStream(ownerUserId, workspaceId, request, emitter, sessionState));
        } catch (TaskRejectedException exception) {
            LOGGER.warn("检索增强生成流式任务被执行器拒绝：知识空间编号={}", workspaceId, exception);
            sendErrorAndComplete(
                    emitter,
                    sessionState,
                    new RagStreamErrorEvent("STREAM_CAPACITY_EXCEEDED", "当前流式任务较多，请稍后重试", true)
            );
        }
        return emitter;
    }

    private void executeStream(
            Long ownerUserId,
            Long workspaceId,
            RagChatRequest request,
            SseEmitter emitter,
            StreamSessionState sessionState
    ) {
        AtomicReference<PreparedRagChat> preparedChatReference = new AtomicReference<>();
        try {
            PreparedRagChat preparedChat = contextAssemblyService.prepareChat(ownerUserId, workspaceId, request);
            preparedChatReference.set(preparedChat);
            sendMetadata(emitter, sessionState, preparedChat);
            sendCitations(emitter, sessionState, preparedChat);

            AtomicInteger deltaSequence = new AtomicInteger();
            StringBuilder completeAnswer = new StringBuilder();
            StreamingGeneratedAnswer generatedAnswer = streamingAnswerGenerator.generate(
                    preparedChat.generationRequest(),
                    delta -> {
                        sendDelta(emitter, sessionState, preparedChat.messageId(), deltaSequence, delta);
                        completeAnswer.append(delta);
                    },
                    sessionState::checkActive
            );
            chatMemoryService.completeAssistant(
                    workspaceId,
                    preparedChat.conversationId(),
                    preparedChat.messageId(),
                    completeAnswer.toString()
            );
            sendToolCalls(emitter, sessionState, preparedChat.messageId(), generatedAnswer);
            // 拒答和模型降级结果不是有效知识内容，禁止继续生成笔记或复习卡片确认单。
            if (!generatedAnswer.metadata().refused()) {
                sendWriteToolProposals(
                        emitter,
                        sessionState,
                        new AgentToolExecutionContext(
                                ownerUserId, workspaceId, preparedChat.conversationId(), preparedChat.messageId()
                        ),
                        request.question(),
                        completeAnswer.toString()
                );
            }
            sendEvent(
                    emitter,
                    sessionState,
                    eventId(preparedChat.messageId(), "complete"),
                    RagStreamEventType.COMPLETE,
                    new RagStreamCompleteEvent(
                            preparedChat.conversationId(),
                            preparedChat.messageId(),
                            deltaSequence.get(),
                            generatedAnswer.answerLength(),
                            generatedAnswer.metadata(),
                            generatedAnswer.usage(),
                            generatedAnswer.toolCalls()
                    )
            );
            completeNormally(emitter, sessionState);
        } catch (RagStreamTerminatedException exception) {
            cancelPreparedAssistant(workspaceId, preparedChatReference.get(), exception.getMessage());
            LOGGER.info(
                    "检索增强生成流式会话提前结束：知识空间编号={}，原因={}",
                    workspaceId,
                    exception.reason().message()
            );
            completeAfterTermination(emitter, sessionState);
        } catch (RuntimeException exception) {
            failPreparedAssistant(workspaceId, preparedChatReference.get(), exception.getMessage());
            LOGGER.error("检索增强生成流式会话异常结束：知识空间编号={}", workspaceId, exception);
            sendErrorAndComplete(
                    emitter,
                    sessionState,
                    new RagStreamErrorEvent("STREAM_GENERATION_FAILED", "流式回答生成失败，请稍后重试", true)
            );
        }
    }

    private void cancelPreparedAssistant(Long workspaceId, PreparedRagChat preparedChat, String reason) {
        if (preparedChat == null) {
            return;
        }
        chatMemoryService.cancelAssistant(
                workspaceId,
                preparedChat.conversationId(),
                preparedChat.messageId(),
                reason
        );
    }

    private void failPreparedAssistant(Long workspaceId, PreparedRagChat preparedChat, String reason) {
        if (preparedChat == null) {
            return;
        }
        chatMemoryService.failAssistant(
                workspaceId,
                preparedChat.conversationId(),
                preparedChat.messageId(),
                reason
        );
    }

    private void sendMetadata(SseEmitter emitter, StreamSessionState sessionState, PreparedRagChat preparedChat) {
        RagStreamMetadataEvent metadata = new RagStreamMetadataEvent(
                preparedChat.conversationId(),
                preparedChat.messageId(),
                preparedChat.generationRequest().promptVersion(),
                streamingAnswerGenerator.generatorType(),
                streamingAnswerGenerator.modelName(),
                preparedChat.citations().size(),
                OffsetDateTime.now()
        );
        sendEvent(
                emitter,
                sessionState,
                eventId(preparedChat.messageId(), "metadata"),
                RagStreamEventType.METADATA,
                metadata
        );
    }

    private void sendCitations(SseEmitter emitter, StreamSessionState sessionState, PreparedRagChat preparedChat) {
        for (RagCitationResponse citation : preparedChat.citations()) {
            RagStreamCitationEvent citationEvent = new RagStreamCitationEvent(citation.index(), citation);
            sendEvent(
                    emitter,
                    sessionState,
                    eventId(preparedChat.messageId(), "citation-" + citation.index()),
                    RagStreamEventType.CITATION,
                    citationEvent
            );
        }
    }

    private void sendToolCalls(
            SseEmitter emitter,
            StreamSessionState sessionState,
            Long messageId,
            StreamingGeneratedAnswer generatedAnswer
    ) {
        for (int index = 0; index < generatedAnswer.toolCalls().size(); index++) {
            int sequence = index + 1;
            sendEvent(
                    emitter,
                    sessionState,
                    eventId(messageId, "tool-call-" + sequence),
                    RagStreamEventType.TOOL_CALL,
                    new RagStreamToolCallEvent(sequence, generatedAnswer.toolCalls().get(index))
            );
        }
    }

    private void sendWriteToolProposals(
            SseEmitter emitter,
            StreamSessionState sessionState,
            AgentToolExecutionContext context,
            String userQuestion,
            String generatedAnswer
    ) {
        try {
            java.util.List<CreatedAgentToolConfirmationResponse> proposals = writeToolProposalService.propose(
                    context, userQuestion, generatedAnswer
            );
            for (int index = 0; index < proposals.size(); index++) {
                int sequence = index + 1;
                sendEvent(
                        emitter,
                        sessionState,
                        eventId(context.messageId(), "tool-confirmation-" + sequence),
                        RagStreamEventType.TOOL_CONFIRMATION_REQUIRED,
                        new RagStreamToolConfirmationRequiredEvent(sequence, proposals.get(index))
                );
            }
        } catch (RuntimeException exception) {
            // 写工具建议是回答后的附加能力，失败时记录原因但不把已经完成的回答标记为失败。
            LOGGER.warn(
                    "生成写工具确认建议失败：知识空间编号={}，消息编号={}",
                    context.workspaceId(),
                    context.messageId(),
                    exception
            );
        }
    }

    private void sendDelta(
            SseEmitter emitter,
            StreamSessionState sessionState,
            Long messageId,
            AtomicInteger sequence,
            String delta
    ) {
        int currentSequence = sequence.incrementAndGet();
        sendEvent(
                emitter,
                sessionState,
                eventId(messageId, "delta-" + currentSequence),
                RagStreamEventType.DELTA,
                new RagStreamDeltaEvent(currentSequence, delta)
        );
    }

    private void sendEvent(
            SseEmitter emitter,
            StreamSessionState sessionState,
            String eventId,
            RagStreamEventType eventType,
            Object data
    ) {
        sessionState.checkActive();
        try {
            emitter.send(SseEmitter.event()
                    .id(eventId)
                    .name(eventType.eventName())
                    .data(data));
        } catch (IOException | IllegalStateException exception) {
            sessionState.terminate(RagStreamTerminationReason.CLIENT_DISCONNECTED);
            throw new RagStreamTerminatedException(RagStreamTerminationReason.CLIENT_DISCONNECTED);
        }
    }

    private void sendErrorAndComplete(
            SseEmitter emitter,
            StreamSessionState sessionState,
            RagStreamErrorEvent errorEvent
    ) {
        if (!sessionState.isActive()) {
            completeAfterTermination(emitter, sessionState);
            return;
        }
        try {
            sendEvent(emitter, sessionState, "error", RagStreamEventType.ERROR, errorEvent);
            completeNormally(emitter, sessionState);
        } catch (RagStreamTerminatedException exception) {
            completeAfterTermination(emitter, sessionState);
        }
    }

    private void registerLifecycleCallbacks(SseEmitter emitter, StreamSessionState sessionState) {
        emitter.onTimeout(() -> sessionState.terminate(RagStreamTerminationReason.TIMED_OUT));
        emitter.onError(exception -> sessionState.terminate(RagStreamTerminationReason.CLIENT_DISCONNECTED));
        emitter.onCompletion(sessionState::transportCompleted);
    }

    private void completeNormally(SseEmitter emitter, StreamSessionState sessionState) {
        if (sessionState.markCompleted()) {
            emitter.complete();
        }
    }

    private void completeAfterTermination(SseEmitter emitter, StreamSessionState sessionState) {
        if (sessionState.markCompleted()) {
            emitter.complete();
        }
    }

    private String eventId(Long messageId, String suffix) {
        return messageId + "-" + suffix;
    }

    /**
     * 单个 SSE 连接的线程安全状态。
     *
     * <p>终止原因只允许首次写入；正常完成标记用于区分服务主动完成与传输层意外关闭。</p>
     */
    private static final class StreamSessionState {

        private final AtomicReference<RagStreamTerminationReason> terminationReason = new AtomicReference<>();
        private final AtomicBoolean completed = new AtomicBoolean();

        private void checkActive() {
            RagStreamTerminationReason reason = terminationReason.get();
            if (reason != null) {
                throw new RagStreamTerminatedException(reason);
            }
            if (completed.get()) {
                throw new RagStreamTerminatedException(RagStreamTerminationReason.CLIENT_DISCONNECTED);
            }
        }

        private void terminate(RagStreamTerminationReason reason) {
            if (!completed.get()) {
                terminationReason.compareAndSet(null, reason);
            }
        }

        private boolean isActive() {
            return !completed.get() && terminationReason.get() == null;
        }

        private boolean markCompleted() {
            return completed.compareAndSet(false, true);
        }

        private void transportCompleted() {
            if (!completed.get()) {
                terminate(RagStreamTerminationReason.CLIENT_DISCONNECTED);
            }
        }
    }
}
