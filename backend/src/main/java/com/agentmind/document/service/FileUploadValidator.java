package com.agentmind.document.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.document.model.DocumentSourceType;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件进入摄取流程前的上传校验器。
 *
 * <p>该层负责空文件、大小、文件名和扩展名校验；PDF 页数、Office 解包规模和真实格式识别
 * 由后续解析器完成。病毒扫描仍可继续加在这里，不需要改变控制层契约。</p>
 */
@Component
public class FileUploadValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "md", "markdown", "txt", "doc", "docx", "html", "htm", "java", "kt", "ts"
    );

    private static final Map<String, DocumentSourceType> SOURCE_TYPE_BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", DocumentSourceType.PDF),
            Map.entry("md", DocumentSourceType.MARKDOWN),
            Map.entry("markdown", DocumentSourceType.MARKDOWN),
            Map.entry("doc", DocumentSourceType.WORD),
            Map.entry("docx", DocumentSourceType.WORD),
            Map.entry("html", DocumentSourceType.WEB_PAGE),
            Map.entry("htm", DocumentSourceType.WEB_PAGE),
            Map.entry("java", DocumentSourceType.CODE),
            Map.entry("kt", DocumentSourceType.CODE),
            Map.entry("ts", DocumentSourceType.CODE),
            Map.entry("txt", DocumentSourceType.TEXT)
    );

    private final long maxUploadSizeBytes;

    public FileUploadValidator(@Value("${agentmind.ingestion.max-upload-size-bytes:20971520}") long maxUploadSizeBytes) {
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    public FileValidationResult validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > maxUploadSizeBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件超过大小限制");
        }

        String safeFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extractExtension(safeFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的文件类型：" + extension);
        }

        DocumentSourceType sourceType = SOURCE_TYPE_BY_EXTENSION.getOrDefault(extension, DocumentSourceType.TEXT);
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        return new FileValidationResult(safeFilename, sourceType, contentType, file.getSize());
    }

    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不能为空");
        }
        String normalized = Path.of(filename).getFileName().toString();
        return normalized.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名必须包含扩展名");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
