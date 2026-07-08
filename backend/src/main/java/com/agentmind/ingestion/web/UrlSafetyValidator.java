package com.agentmind.ingestion.web;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Basic URL safety validator for web ingestion.
 *
 * <p>Stage 4 blocks non-HTTP schemes, localhost, loopback hosts, link-local addresses and common private IPv4
 * ranges. DNS resolution based private-address checks can be added in the network adapter before production use.</p>
 */
@Component
public class UrlSafetyValidator {

    public URI validatePublicHttpUrl(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "URL only supports http or https");
            }
            if (!StringUtils.hasText(host) || isBlockedHost(host)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "URL host is not allowed");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid URL format");
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
