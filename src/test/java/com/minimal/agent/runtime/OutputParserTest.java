package com.minimal.agent.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputParserTest {

    private final OutputParser parser = new OutputParser();

    @Test
    void parseCallTool() {
        String raw = "{\"thought\":\"需要计算\",\"action\":\"call_tool\","
                + "\"tool_name\":\"calculator\",\"tool_args\":{\"expression\":\"1+2\"}}";
        AgentAction action = parser.parse(raw);
        assertEquals(AgentAction.Type.CALL_TOOL, action.type());
        assertEquals("calculator", action.toolName());
        assertEquals("1+2", action.toolArgs().get("expression"));
        assertEquals("需要计算", action.thought());
    }

    @Test
    void parseFinalAnswer() {
        String raw = "{\"thought\":\"直接回答\",\"action\":\"final_answer\",\"final_answer\":\"你好呀\"}";
        AgentAction action = parser.parse(raw);
        assertEquals(AgentAction.Type.FINAL_ANSWER, action.type());
        assertEquals("你好呀", action.finalAnswer());
    }

    @Test
    void tolerateMarkdownCodeFence() {
        String raw = "```json\n{\"thought\":\"t\",\"action\":\"final_answer\",\"final_answer\":\"ok\"}\n```";
        assertEquals("ok", parser.parse(raw).finalAnswer());
    }

    @Test
    void tolerateSurroundingText() {
        String raw = "好的，我来想一下。\n{\"thought\":\"t\",\"action\":\"final_answer\",\"final_answer\":\"答案\"}\n以上。";
        assertEquals("答案", parser.parse(raw).finalAnswer());
    }

    @Test
    void rejectGarbage() {
        assertThrows(OutputParseException.class, () -> parser.parse("我完全不是 JSON"));
    }

    @Test
    void rejectEmptyAnswer() {
        assertThrows(OutputParseException.class,
                () -> parser.parse("{\"action\":\"final_answer\",\"final_answer\":\"\"}"));
    }

    @Test
    void rejectUnknownAction() {
        assertThrows(OutputParseException.class,
                () -> parser.parse("{\"action\":\"fly_to_moon\"}"));
    }

    @Test
    void rejectMissingToolName() {
        assertThrows(OutputParseException.class,
                () -> parser.parse("{\"action\":\"call_tool\",\"tool_args\":{}}"));
    }

    @Test
    void missingToolArgsBecomeEmptyMap() {
        AgentAction action = parser.parse(
                "{\"action\":\"call_tool\",\"tool_name\":\"search\"}");
        assertTrue(action.toolArgs().isEmpty());
    }
}
