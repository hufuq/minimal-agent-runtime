package com.minimal.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimal.agent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

/**
 * 系统提示词构建器：把工具清单（名称 + 描述 + Schema）和输出协议注入 system prompt。
 * 模型只能依据这里的 Schema 自主决策调用哪个工具、如何填参。
 */
@Component
public class SystemPromptBuilder {

    private final ToolRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    public SystemPromptBuilder(ToolRegistry registry) {
        this.registry = registry;
    }

    public String build() {
        String toolsJson;
        try {
            toolsJson = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(registry.describeAll());
        } catch (Exception e) {
            throw new IllegalStateException("工具 Schema 序列化失败", e);
        }
        return """
                你是一个会调用工具的 AI 助手。你可以使用的工具如下（JSON 数组，含名称、描述、参数 Schema）：

                %s

                【输出协议——必须严格遵守】
                你的每一次回复都必须是、且只能是一个 JSON 对象，不要输出任何其他文字，不要使用 markdown 代码围栏。两种合法形态：

                1. 需要调用工具时：
                {"thought":"你的简要思考","action":"call_tool","tool_name":"工具名","tool_args":{"参数名":"参数值"}}

                2. 可以直接回复用户时（包括：无需工具的问答、工具结果已足够回答、工具失败需要告知用户）：
                {"thought":"你的简要思考","action":"final_answer","final_answer":"给用户的完整回复"}

                【决策规则】
                - 只有上述工具能解决或辅助解决用户问题时才调用工具，其余情况直接 final_answer。
                - 一次只调用一个工具；拿到工具结果后再决定继续调用还是 final_answer。
                - 工具结果中出现 [工具结果] 前缀的消息即为上一轮的调用返回，请基于它回答或继续决策。
                - 工具调用失败时，可换个参数重试一次；仍失败则用 final_answer 向用户说明。
                - 对话历史中出现的信息可以直接利用，支持用户追问。
                """.formatted(toolsJson);
    }
}
