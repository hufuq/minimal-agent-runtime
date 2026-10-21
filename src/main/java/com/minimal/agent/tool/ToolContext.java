package com.minimal.agent.tool;

/**
 * 单次工具调用的上下文。目前只携带 sessionId，
 * 供 todo 这类"按会话隔离状态"的工具使用。
 */
public record ToolContext(String sessionId) {
}
