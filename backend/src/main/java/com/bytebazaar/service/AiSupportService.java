package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.AiReply;
import com.bytebazaar.entity.User;

/**
 * Abstraction for the 24x7 AI assistant. The bundled RuleBasedAiSupportService answers ONLY from the authenticated
 * user's real database records. An LLM-backed implementation (key from the AI_API_KEY env var) can replace it,
 * but must be given the same database facts and must never invent order data.
 */
public interface AiSupportService {
    AiReply answer(User user, String message, Long orderId);
}
