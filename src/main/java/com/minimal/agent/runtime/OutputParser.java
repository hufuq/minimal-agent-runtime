package com.minimal.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 输出解析器（自写，不依赖任何框架）。
 *
 * 约定模型每次输出一个 JSON 对象：
 *   {"thought":"...","action":"call_tool","tool_name":"...","tool_args":{...}}
 *   {"thought":"...","action":"final_answer","final_answer":"..."}
 *
 * 容错策略（按严格程度递进）：
 * 1. 直接整串解析；
 * 2. 剥掉 ```json 代码围栏后解析；
 * 3. 截取第一个 '{' 到最后一个 '}' 的子串解析（容忍前后废话）。
 * 三步都失败则抛 OutputParseException，交给 Runtime 决策（重试/兜底）。
 */
@Component
public class OutputParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public AgentAction parse(String raw) throws OutputParseException {
        if (raw == null || raw.isBlank()) {
            throw new OutputParseException("模型输出为空");
        }
        JsonNode node = tryParse(raw.trim());
        if (node == null) {
            node = tryParse(stripCodeFence(raw.trim()));
        }
        if (node == null) {
            node = tryParse(extractJsonObject(raw));
        }
        if (node == null) {
            throw new OutputParseException("无法从模型输出中解析出 JSON 对象");
        }
        return toAction(node, raw);
    }

    private AgentAction toAction(JsonNode node, String raw) {
        String thought = node.path("thought").asText("");
        String action = node.path("action").asText("");
        return switch (action) {
            case "call_tool" -> {
                String toolName = node.path("tool_name").asText("");
                if (toolName.isBlank()) {
                    throw new OutputParseException("action=call_tool 但缺少 tool_name");
                }
                yield AgentAction.callTool(thought, toolName,
                        toMap(node.path("tool_args")), raw);
            }
            case "final_answer" -> {
                String answer = node.path("final_answer").asText("");
                if (answer.isBlank()) {
                    throw new OutputParseException("action=final_answer 但 final_answer 为空");
                }
                yield AgentAction.finalAnswer(thought, answer, raw);
            }
            default -> throw new OutputParseException(
                    "非法 action: '" + action + "'，应为 call_tool 或 final_answer");
        };
    }

    private JsonNode tryParse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            JsonNode n = mapper.readTree(s);
            return n != null && n.isObject() ? n : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String stripCodeFence(String s) {
        String out = s;
        if (out.startsWith("```")) {
            int firstNl = out.indexOf('\n');
            out = firstNl > 0 ? out.substring(firstNl + 1) : out;
        }
        if (out.endsWith("```")) {
            out = out.substring(0, out.length() - 3);
        }
        return out.trim();
    }

    private String extractJsonObject(String s) {
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return s.substring(start, end + 1);
    }

    private Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                map.put(e.getKey(), mapper.convertValue(e.getValue(), Object.class));
            }
        }
        return map;
    }
}
