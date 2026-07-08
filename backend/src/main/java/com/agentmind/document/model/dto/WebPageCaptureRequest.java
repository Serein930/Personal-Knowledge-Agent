package com.agentmind.document.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * URL 采集请求 DTO。
 *
 * <p>这里只做基础字段约束。SSRF 防护、URL DNS 解析和内网地址拦截会在后续
 * Web 采集服务中实现，避免把安全逻辑塞进 DTO。</p>
 */
public record WebPageCaptureRequest(
        @NotBlank(message = "url 不能为空")
        String url,

        @Size(max = 120, message = "标题长度不能超过 120 个字符")
        String title,

        List<String> tags
) {
}
