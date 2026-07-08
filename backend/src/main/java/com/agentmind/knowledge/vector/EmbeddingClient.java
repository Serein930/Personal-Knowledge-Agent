package com.agentmind.knowledge.vector;

/**
 * Port for converting text into an embedding vector.
 *
 * <p>Keeping this as a project-level abstraction lets the current deterministic local implementation be replaced by
 * Spring AI's EmbeddingModel later without changing ingestion or retrieval services.</p>
 */
public interface EmbeddingClient {

    float[] embed(String text);
}
