package com.minimal.agent.tool.impl;

import com.minimal.agent.tool.Tool;
import com.minimal.agent.tool.ToolContext;
import com.minimal.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 搜索工具（mock 实现）：按关键词命中内置语料返回固定结果，
 * 未命中时返回通用提示。接入真实搜索时只需替换本类的检索逻辑，
 * 对 Agent 主流程零影响——这正是工具抽象的意义。
 */
@Component
public class MockSearchTool implements Tool {

    private static final Map<String, String> CORPUS = Map.of(
            "agent", "2026 年 AI Agent 趋势：1) 多 Agent 协作成为企业落地主流形态；"
                    + "2) 工具调用的幂等与可观测性成为工程重点；3) 端侧小模型 + 云端大模型混合调度普及。",
            "天气", "气象部门发布：未来三天全国大部晴到多云，华南沿海有阵雨。",
            "周报", "高效周报写法：结论先行、数据支撑、下周计划量化到可验收指标。");

    @Override
    public String name() {
        return "search";
    }

    @Override
    public String description() {
        return "联网搜索信息（当前为 mock 实现），用于查询新闻、趋势、资料等开放性问题";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "搜索关键词或问题")),
                "required", List.of("query"));
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        Object query = args.get("query");
        if (query == null || query.toString().isBlank()) {
            return ToolResult.error("参数 query 不能为空");
        }
        String q = query.toString().toLowerCase();
        for (Map.Entry<String, String> e : CORPUS.entrySet()) {
            if (q.contains(e.getKey())) {
                return ToolResult.ok("[mock 搜索结果] " + e.getValue());
            }
        }
        return ToolResult.ok("[mock 搜索结果] 未检索到与「" + query + "」强相关的资料，"
                + "建议换关键词重试。（这是模拟数据，真实环境接入搜索 API）");
    }
}
