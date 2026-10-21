package com.minimal.agent.tool.impl;

import com.minimal.agent.tool.Tool;
import com.minimal.agent.tool.ToolContext;
import com.minimal.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 天气工具（mock 实现）：由城市名哈希确定性地生成天气，
 * 同一城市结果恒定，方便测试断言与演示复现。
 */
@Component
public class WeatherTool implements Tool {

    private static final String[] CONDITIONS = {"晴", "多云", "阴", "小雨", "雷阵雨"};

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "查询指定城市的当日天气（当前为 mock 实现，结果确定性生成）";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of(
                                "type", "string",
                                "description", "城市名，如 广州、厦门")),
                "required", List.of("city"));
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String city = String.valueOf(args.getOrDefault("city", "")).trim();
        if (city.isEmpty()) {
            return ToolResult.error("参数 city 不能为空");
        }
        int h = Math.abs(city.hashCode());
        String cond = CONDITIONS[h % CONDITIONS.length];
        int temp = 15 + h % 18;
        return ToolResult.ok(String.format("[mock 天气] %s：%s，气温 %d℃，湿度 %d%%",
                city, cond, temp, 40 + h % 50));
    }
}
