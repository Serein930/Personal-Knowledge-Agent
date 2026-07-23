package com.agentmind.common.config;

import java.util.Locale;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 真实聊天模型配置校验器。
 *
 * <p>当前项目通过 Spring AI 的 OpenAI Chat Completions 适配器访问兼容服务，
 * 因此不能把 Anthropic 协议地址直接填入基础地址。该校验在应用接受请求前失败，
 * 避免用户完成检索后才从流式问答中收到不明确的 404 降级结果。</p>
 */
@Component
@Profile({"local-ai", "production"})
public class ChatModelConfigurationValidator implements SmartInitializingSingleton {

    private final Environment environment;

    public ChatModelConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!"openai".equalsIgnoreCase(environment.getProperty("spring.ai.model.chat", "openai"))) {
            return;
        }

        String baseUrl = property("spring.ai.openai.chat.base-url", "spring.ai.openai.base-url");
        String completionsPath = environment.getProperty(
                "spring.ai.openai.chat.completions-path",
                "/v1/chat/completions"
        );
        String modelName = environment.getProperty("spring.ai.openai.chat.options.model");

        requireOpenAiCompatibleAddress(baseUrl);
        requireNonDuplicatedVersionPath(baseUrl, completionsPath);
        requireValidDeepSeekModelName(baseUrl, modelName);
    }

    private void requireOpenAiCompatibleAddress(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("真实聊天模型配置错误：AGENTMIND_CHAT_BASE_URL 不能为空");
        }
        String normalized = trimTrailingSlash(baseUrl).toLowerCase(Locale.ROOT);
        if (normalized.endsWith("/anthropic") || normalized.contains("/anthropic/")) {
            throw new IllegalStateException(
                    "真实聊天模型配置错误：当前项目使用 OpenAI Chat Completions 协议，"
                            + "AGENTMIND_CHAT_BASE_URL 不能包含 /anthropic；"
                            + "DeepSeek 请配置为 https://api.deepseek.com"
            );
        }
    }

    private void requireNonDuplicatedVersionPath(String baseUrl, String completionsPath) {
        String normalizedBaseUrl = trimTrailingSlash(baseUrl).toLowerCase(Locale.ROOT);
        String normalizedPath = completionsPath == null ? "" : completionsPath.trim().toLowerCase(Locale.ROOT);
        if (normalizedBaseUrl.endsWith("/v1") && normalizedPath.startsWith("/v1/")) {
            throw new IllegalStateException(
                    "真实聊天模型配置错误：基础地址和请求路径重复包含 /v1；"
                            + "请使用“基础地址不含 /v1 + /v1/chat/completions”，"
                            + "或“基础地址含 /v1 + /chat/completions”"
            );
        }
    }

    private void requireValidDeepSeekModelName(String baseUrl, String modelName) {
        String normalizedBaseUrl = trimTrailingSlash(baseUrl).toLowerCase(Locale.ROOT);
        if (!normalizedBaseUrl.startsWith("https://api.deepseek.com")
                || !StringUtils.hasText(modelName)) {
            return;
        }
        String normalizedModel = modelName.trim().toLowerCase(Locale.ROOT);
        if (normalizedModel.startsWith("deepseekv4-")) {
            throw new IllegalStateException(
                    "真实聊天模型配置错误：DeepSeek 模型名称缺少连字符；"
                            + "请将 AGENTMIND_CHAT_MODEL 配置为 deepseek-v4-pro 或供应商实际提供的模型编号"
            );
        }
    }

    private String property(String primaryName, String fallbackName) {
        String primaryValue = environment.getProperty(primaryName);
        return StringUtils.hasText(primaryValue)
                ? primaryValue
                : environment.getProperty(fallbackName);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }
}
