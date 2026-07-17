package com.agentmind.document.parser;

/**
 * 文档内容无法安全提取时抛出的领域异常。
 *
 * <p>异常只携带适合写入任务状态和返回客户端的稳定中文原因。底层 PDFBox、Tika 或 POI
 * 异常作为 cause 保留给服务端日志，不把解析器内部路径或实现细节直接暴露给用户。</p>
 */
public class DocumentTextExtractionException extends RuntimeException {

    public DocumentTextExtractionException(String message) {
        super(message);
    }

    public DocumentTextExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
