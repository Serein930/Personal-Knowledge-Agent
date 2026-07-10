package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.TokenUsageResponse;

/**
 * Result returned by an answer generator.
 *
 * <p>The abstraction mirrors the fields real model providers usually expose: generated content and token usage.
 * Mock generation keeps usage at zero, while a Spring AI implementation can fill provider metadata later.</p>
 */
public record GeneratedAnswer(
        String content,
        TokenUsageResponse usage
) {
}
