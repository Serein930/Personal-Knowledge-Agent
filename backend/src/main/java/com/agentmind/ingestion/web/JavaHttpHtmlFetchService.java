package com.agentmind.ingestion.web;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 基于标准库网络客户端的网页内容抓取实现。
 *
 * <p>该适配器设置超时时间、跟随常规重定向，并限制响应体大小。本阶段暂不处理依赖脚本渲染的页面；
 * 后续可以在同一接口后增加浏览器渲染兜底适配器。</p>
 */
@Service
public class JavaHttpHtmlFetchService implements HtmlFetchService {

    private final HttpClient httpClient;
    private final Duration timeout;
    private final int maxBytes;

    public JavaHttpHtmlFetchService(
            @Value("${agentmind.ingestion.web-fetch-timeout-seconds:8}") long timeoutSeconds,
            @Value("${agentmind.ingestion.web-fetch-max-bytes:1048576}") int maxBytes
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxBytes = maxBytes;
    }

    @Override
    public HtmlFetchResult fetch(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", "AgentMindBot/0.1 (+personal knowledge ingestion)")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "HTML 抓取失败，HTTP 状态码：" + statusCode);
        }

        byte[] bytes;
        try (InputStream body = response.body()) {
            bytes = body.readNBytes(maxBytes + 1);
        }
        if (bytes.length > maxBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "HTML 响应体超过抓取大小限制");
        }

        String contentType = response.headers().firstValue("content-type").orElse("text/html");
        return new HtmlFetchResult(uri, statusCode, contentType, new String(bytes, StandardCharsets.UTF_8));
    }
}
