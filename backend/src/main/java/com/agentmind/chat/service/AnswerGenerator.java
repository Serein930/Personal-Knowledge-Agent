package com.agentmind.chat.service;

/**
 * 检索增强生成回答生成端口。
 *
 * <p>应用服务只依赖这个接口，不直接依赖具体模型客户端。这样当前阶段可以使用模拟生成器，
 * 后续也可以平滑替换为真实模型适配器。</p>
 */
public interface AnswerGenerator {

    GeneratedAnswer generate(AnswerGenerationRequest request);
}
