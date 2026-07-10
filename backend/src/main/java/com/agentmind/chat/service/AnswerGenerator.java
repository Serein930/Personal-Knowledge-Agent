package com.agentmind.chat.service;

/**
 * Port for generating a final answer from retrieved RAG context.
 *
 * <p>The application service depends on this small interface instead of a concrete model client. This keeps Stage 6
 * testable without API keys and gives the next Spring AI integration a clean adapter boundary.</p>
 */
public interface AnswerGenerator {

    GeneratedAnswer generate(AnswerGenerationRequest request);
}
