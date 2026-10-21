package com.minimal.agent.tool;

/**
 * 工具执行结果。success=false 时 content 为错误描述，
 * 错误信息会回灌给 LLM，让模型自行决定重试、换方案还是告知用户。
 */
public record ToolResult(boolean success, String content) {

    public static ToolResult ok(String content) {
        return new ToolResult(true, content);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, "ERROR: " + message);
    }
}
