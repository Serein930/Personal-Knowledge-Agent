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
 * Validates uploaded knowledge files before they enter the ingestion pipeline.
 *
 * <p>Stage 4 keeps this validator intentionally focused: empty file, size, filename and extension checks.
 * Content sniffing, virus scanning, PDF page limits and archive restrictions can be added here later without
 * changing the controller contract.</p>
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Uploaded file must not be empty");
        }
        if (file.getSize() > maxUploadSizeBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Uploaded file exceeds size limit");
        }

        String safeFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extractExtension(safeFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported file type: " + extension);
        }

        DocumentSourceType sourceType = SOURCE_TYPE_BY_EXTENSION.getOrDefault(extension, DocumentSourceType.TEXT);
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        return new FileValidationResult(safeFilename, sourceType, contentType, file.getSize());
    }

    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Filename must not be blank");
        }
        String normalized = Path.of(filename).getFileName().toString();
        return normalized.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Filename must contain an extension");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
