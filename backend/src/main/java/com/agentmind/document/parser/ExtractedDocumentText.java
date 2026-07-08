package com.agentmind.document.parser;

/**
 * Normalized text extracted from a source document.
 *
 * <p>The parser layer returns text that is ready for chunking, while keeping enough structure for later RAG
 * metadata. At this stage we only expose title and plain text; richer metadata can be added when PDF/Word parsers
 * are introduced.</p>
 */
public record ExtractedDocumentText(
        String title,
        String text
) {
}
