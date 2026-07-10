package com.agentmind.document.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 链接采集请求数据传输对象。
 *
 * <p>这里只做基础字段约束。服务端请求伪造防护、链接域名解析和内网地址拦截会在后续
 * 网页采集服务中实现，避免把安全逻辑塞进数据传输对象。</p>
 */
public record WebPageCaptureRequest(
        @NotBlank(message = "url 不能为空")
        String url,

        @Size(max = 120, message = "标题长度不能超过 120 个字符")
        String title,

        List<String> tags
) {
}
