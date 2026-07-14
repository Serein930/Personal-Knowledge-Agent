package com.agentmind.chat.memory.repository;

/**
 * 内存会话记忆适配器的公共仓储契约测试。
 */
class InMemoryChatMemoryRepositoryContractTests extends ChatMemoryRepositoryContractTests {

    private final InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();

    @Override
    protected ChatMemoryRepository repository() {
        return repository;
    }
}
