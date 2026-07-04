package com.agentmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentMind 后端应用入口。
 *
 * <p>当前阶段只启动基础 Web 服务、统一响应和健康检查能力。数据库、AI 模型、
 * 对象存储等外部依赖会在后续阶段按规划逐步接入。</p>
 */
@SpringBootApplication
public class AgentMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentMindApplication.class, args);
    }
}
