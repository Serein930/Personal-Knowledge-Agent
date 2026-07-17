package com.agentmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后端应用入口。
 *
 * <p>应用已经具备认证授权、知识摄取、混合检索、检索增强生成、Agent 工具、学习系统、
 * 评估观测和生产基础设施适配能力。具体实现通过配置在本地内存模式与生产外部依赖之间切换。</p>
 */
@SpringBootApplication
public class AgentMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentMindApplication.class, args);
    }
}
