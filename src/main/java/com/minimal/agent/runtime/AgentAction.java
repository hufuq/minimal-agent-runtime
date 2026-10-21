package com.minimal.agent.runtime;

import java.util.Map;

/**
 * 解析后的 LLM 输出，两种形态：
 * - callTool：调用工具，携带工具名与参数
 * - finalAnswer：直接回复用户
 * thought（思考过程）两种形态都会有，记录到 session 与 trace，不回灌历史上下文（见 README memory 策略）。
 */
public record AgentAction(Type type, String thought, String toolName,
                          Map<String, Object> toolArgs, String finalAnswer, String raw) {

    public enum Type {CALL_TOOL, FINAL_ANSWER}

    public static AgentAction callTool(String thought, String toolName,
                                       Map<String, Object> toolArgs, String raw) {
        return new AgentAction(Type.CALL_TOOL, thought, toolName, toolArgs, null, raw);
    }

    public static AgentAction finalAnswer(String thought, String answer, String raw) {
        return new AgentAction(Type.FINAL_ANSWER, thought, null, null, answer, raw);
    }
}
