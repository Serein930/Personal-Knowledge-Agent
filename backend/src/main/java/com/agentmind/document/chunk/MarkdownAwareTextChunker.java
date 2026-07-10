package com.agentmind.document.chunk;

import com.agentmind.document.model.DocumentSourceType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 尽量尊重标记文档标题和段落边界的基础切分器。
 *
 * <p>该算法刻意保持确定性且不依赖外部组件。它先按标题分组，再对过长小节做固定重叠切分。
 * 这样可以为向量库提供稳定片段编号，并为引用预览保留足够上下文。</p>
 */
@Service
public class MarkdownAwareTextChunker implements TextChunker {

    private static final ChunkingOptions DEFAULT_OPTIONS = ChunkingOptions.defaults();

    @Override
    public List<DocumentChunk> chunk(Long documentId, DocumentSourceType sourceType, String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<Section> sections = sourceType == DocumentSourceType.MARKDOWN
                ? splitMarkdownSections(text)
                : List.of(new Section("", text.trim(), 0));
        List<DocumentChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            appendSectionChunks(documentId, section, chunks);
        }
        return List.copyOf(chunks);
    }

    private List<Section> splitMarkdownSections(String text) {
        List<Section> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentHeading = "";
        int sectionStart = 0;
        int offset = 0;

        for (String line : text.split("\n", -1)) {
            String normalizedLine = line + "\n";
            if (isHeading(line) && current.length() > 0) {
                sections.add(new Section(currentHeading, current.toString().trim(), sectionStart));
                current.setLength(0);
                sectionStart = offset;
            }
            if (isHeading(line)) {
                currentHeading = line.replaceFirst("^#{1,6}\\s*", "").trim();
            }
            current.append(normalizedLine);
            offset += normalizedLine.length();
        }

        if (current.length() > 0) {
            sections.add(new Section(currentHeading, current.toString().trim(), sectionStart));
        }
        return sections;
    }

    private boolean isHeading(String line) {
        return line != null && line.matches("^#{1,6}\\s+.+");
    }

    private void appendSectionChunks(Long documentId, Section section, List<DocumentChunk> chunks) {
        String content = section.content();
        int maxChars = DEFAULT_OPTIONS.maxChars();
        int overlapChars = DEFAULT_OPTIONS.overlapChars();
        int start = 0;

        while (start < content.length()) {
            int end = chooseChunkEnd(content, start, maxChars);
            String chunkText = content.substring(start, end).trim();
            if (StringUtils.hasText(chunkText)) {
                int sequence = chunks.size();
                chunks.add(new DocumentChunk(
                        documentId + "-" + sequence,
                        documentId,
                        sequence,
                        section.headingPath(),
                        chunkText,
                        section.charStart() + start,
                        section.charStart() + end
                ));
            }
            if (end >= content.length()) {
                break;
            }
            start = Math.max(end - overlapChars, start + 1);
        }
    }

    private int chooseChunkEnd(String content, int start, int maxChars) {
        int hardEnd = Math.min(start + maxChars, content.length());
        if (hardEnd == content.length()) {
            return hardEnd;
        }

        int paragraphBreak = content.lastIndexOf("\n\n", hardEnd);
        if (paragraphBreak > start + maxChars / 2) {
            return paragraphBreak;
        }

        int sentenceBreak = Math.max(content.lastIndexOf("。", hardEnd), content.lastIndexOf(".", hardEnd));
        if (sentenceBreak > start + maxChars / 2) {
            return sentenceBreak + 1;
        }

        return hardEnd;
    }

    private record Section(String headingPath, String content, int charStart) {
    }
}
