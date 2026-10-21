package com.minimal.agent.tool;

import java.util.Map;

/**
 * 工具统一抽象。每个工具向注册表提供：名称、自然语言描述、入参 JSON Schema、执行函数。
 * LLM 基于名称 + 描述 + Schema 自主决策是否调用、如何填参。
 */
public interface Tool {

    /** 工具名，LLM 调用时以此为准，建议小写下划线 */
    String name();

    /** 给 LLM 看的能力描述，写得越清楚，模型选得越准 */
    String description();

    /**
     * 入参 JSON Schema（JSON Schema draft-07 子集），
     * 既用于 prompt 展示给模型，也用于调用前的参数校验。
     */
    Map<String, Object> parametersSchema();

    /**
     * 执行工具。实现内部必须自行捕获业务异常并通过 ToolResult.error 返回，
     * 不允许把异常抛给 Agent 主循环导致整个会话崩溃。
     *
     * @param args LLM 填写的参数（已过 Schema 必填校验）
     * @param ctx  调用上下文（当前 sessionId 等），需要会话态的工具使用
     */
    ToolResult execute(Map<String, Object> args, ToolContext ctx);
}
