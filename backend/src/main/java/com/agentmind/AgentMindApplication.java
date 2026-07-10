package com.agentmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentMind 后端应用入口。
 *
 * <p>当前阶段已经具备基础 Web 服务、统一响应、文档摄取、向量检索和 RAG mock 问答能力。
 * 数据库持久化、真实 AI 模型和对象存储等外部依赖会按后续阶段逐步接入。</p>
 */
@SpringBootApplication
public class AgentMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentMindApplication.class, args);
    }
}
