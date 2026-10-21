package com.minimal.agent.tool;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表：Agent 启动时收集所有 Tool 实现，
 * 向 LLM 提供统一的工具清单（名称 + 描述 + Schema），按名分发调用。
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        toolList.forEach(this::register);
    }

    public final void register(Tool tool) {
        Tool prev = tools.putIfAbsent(tool.name(), tool);
        if (prev != null) {
            throw new IllegalStateException("重复的工具名: " + tool.name());
        }
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** 生成给 LLM 看的工具清单（名称 + 描述 + 参数 Schema） */
    public List<Map<String, Object>> describeAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tool t : tools.values()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("name", t.name());
            one.put("description", t.description());
            one.put("parameters", t.parametersSchema());
            list.add(one);
        }
        return list;
    }
}
