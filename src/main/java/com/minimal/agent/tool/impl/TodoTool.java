package com.minimal.agent.tool.impl;

import com.minimal.agent.tool.Tool;
import com.minimal.agent.tool.ToolContext;
import com.minimal.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 待办工具：按 sessionId 隔离的待办清单。
 * 正好覆盖笔试题的 session 场景——同一用户的"窗口1记待办"和"窗口2记待办"互不可见。
 * 生产环境可把存储替换为 Redis（key: agent:todo:{sessionId}，TTL 与 session 一致）。
 */
@Component
public class TodoTool implements Tool {

    /** sessionId -> 待办列表。CopyOnWriteArrayList 保证读写并发安全 */
    private final Map<String, List<String>> todos = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "todo";
    }

    @Override
    public String description() {
        return "管理当前会话的待办事项：add 新增、list 查看、done 完成（按序号删除）、clear 清空";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of("add", "list", "done", "clear"),
                                "description", "要执行的待办操作"),
                        "item", Map.of(
                                "type", "string",
                                "description", "待办内容，action=add 时必填"),
                        "index", Map.of(
                                "type", "integer",
                                "description", "待办序号（从 1 开始），action=done 时必填")),
                "required", List.of("action"));
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String action = String.valueOf(args.getOrDefault("action", "")).trim();
        List<String> list = todos.computeIfAbsent(ctx.sessionId(), k -> new CopyOnWriteArrayList<>());
        try {
            return switch (action) {
                case "add" -> {
                    String item = String.valueOf(args.getOrDefault("item", "")).trim();
                    if (item.isEmpty()) {
                        yield ToolResult.error("action=add 时 item 不能为空");
                    }
                    list.add(item);
                    yield ToolResult.ok("已添加待办（第 " + list.size() + " 项）: " + item);
                }
                case "list" -> {
                    if (list.isEmpty()) {
                        yield ToolResult.ok("当前会话没有待办事项");
                    }
                    StringBuilder sb = new StringBuilder("当前待办：");
                    for (int i = 0; i < list.size(); i++) {
                        sb.append('\n').append(i + 1).append(". ").append(list.get(i));
                    }
                    yield ToolResult.ok(sb.toString());
                }
                case "done" -> {
                    Object idx = args.get("index");
                    if (!(idx instanceof Number n)) {
                        yield ToolResult.error("action=done 时 index 必须为数字");
                    }
                    int i = n.intValue();
                    if (i < 1 || i > list.size()) {
                        yield ToolResult.error("序号超出范围，当前共 " + list.size() + " 项");
                    }
                    yield ToolResult.ok("已完成并移除待办: " + list.remove(i - 1));
                }
                case "clear" -> {
                    list.clear();
                    yield ToolResult.ok("待办已清空");
                }
                default -> ToolResult.error("不支持的 action: " + action + "，可选 add/list/done/clear");
            };
        } catch (RuntimeException e) {
            return ToolResult.error("待办操作失败: " + e.getMessage());
        }
    }
}
