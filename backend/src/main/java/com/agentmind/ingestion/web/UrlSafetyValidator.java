package com.agentmind.ingestion.web;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 网页摄取使用的基础链接安全校验器。
 *
 * <p>当前阶段会阻止非网页协议、本地主机、回环地址、链路本地地址和常见私有网段。
 * 生产使用前可以在网络适配层继续增加基于域名解析结果的内网地址检查。</p>
 */
@Component
public class UrlSafetyValidator {

    public URI validatePublicHttpUrl(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "链接只支持 http 或 https");
            }
            if (!StringUtils.hasText(host) || isBlockedHost(host)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "链接主机不允许访问");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "链接格式不合法");
        }
    }

    private boolean isBlockedHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || normalizedHost.startsWith("127.")
                || normalizedHost.startsWith("10.")
                || isPrivate172Address(normalizedHost)
                || normalizedHost.startsWith("192.168.")
                || normalizedHost.startsWith("169.254.")
                || normalizedHost.equals("0.0.0.0")
                || normalizedHost.equals("::1");
    }

    private boolean isPrivate172Address(String host) {
        for (int secondOctet = 16; secondOctet <= 31; secondOctet++) {
            if (host.startsWith("172." + secondOctet + ".")) {
                return true;
            }
        }
        return false;
    }
}
