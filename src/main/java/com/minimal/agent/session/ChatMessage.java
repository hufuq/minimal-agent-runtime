package com.minimal.agent.session;

import java.time.Instant;

/**
 * 会话中的一条消息记录。
 *
 * type 说明（哪些回灌 LLM 上下文、哪些只落存储，见 ContextAssembler 与 README）：
 * - USER：用户输入，回灌
 * - ASSISTANT_THOUGHT：模型思考过程，只落存储与 trace，不回灌（省 token、防膨胀）
 * - TOOL_CALL：工具调用记录（模型原始 JSON 输出），回灌，维持追问时的语义连贯
 * - TOOL_RESULT：工具返回，回灌（超长截断）
 * - FINAL_ANSWER：最终答复，回灌
 */
public record ChatMessage(Role role, Type type, String content, Instant timestamp) {

    public enum Role {USER, ASSISTANT}

    public enum Type {USER, ASSISTANT_THOUGHT, TOOL_CALL, TOOL_RESULT, FINAL_ANSWER}

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, Type.USER, content, Instant.now());
    }

    public static ChatMessage thought(String content) {
        return new ChatMessage(Role.ASSISTANT, Type.ASSISTANT_THOUGHT, content, Instant.now());
    }

    public static ChatMessage toolCall(String rawJson) {
        return new ChatMessage(Role.ASSISTANT, Type.TOOL_CALL, rawJson, Instant.now());
    }

    public static ChatMessage toolResult(String content) {
        return new ChatMessage(Role.USER, Type.TOOL_RESULT, content, Instant.now());
    }

    public static ChatMessage finalAnswer(String content) {
        return new ChatMessage(Role.ASSISTANT, Type.FINAL_ANSWER, content, Instant.now());
    }
}
