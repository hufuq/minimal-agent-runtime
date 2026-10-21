package com.minimal.agent.context;

import com.minimal.agent.session.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAssemblerTest {

    @Test
    void thoughtIsNotFedBackToContext() {
        ContextAssembler assembler = new ContextAssembler(20, 500);
        List<ChatMessage> history = List.of(
                ChatMessage.user("1+1 等于几"),
                ChatMessage.thought("用户问简单算术，可以直接答"),
                ChatMessage.finalAnswer("等于 2"));
        List<Map<String, String>> msgs = assembler.buildHistoryMessages(history);
        assertEquals(2, msgs.size(), "思考过程不应回灌上下文");
        assertEquals("user", msgs.get(0).get("role"));
        assertEquals("assistant", msgs.get(1).get("role"));
        assertEquals("等于 2", msgs.get(1).get("content"));
    }

    @Test
    void fifoTruncationKeepsMostRecent() {
        ContextAssembler assembler = new ContextAssembler(4, 500);
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            history.add(ChatMessage.user("第" + i + "条"));
        }
        List<Map<String, String>> msgs = assembler.buildHistoryMessages(history);
        assertEquals(4, msgs.size(), "超出窗口应按 FIFO 丢弃最早的");
        assertEquals("第3条", msgs.get(0).get("content"));
        assertEquals("第6条", msgs.get(3).get("content"));
    }

    @Test
    void toolResultIsTruncated() {
        ContextAssembler assembler = new ContextAssembler(20, 10);
        List<ChatMessage> history = List.of(
                ChatMessage.toolResult("[工具结果] search => " + "x".repeat(100)));
        List<Map<String, String>> msgs = assembler.buildHistoryMessages(history);
        assertEquals(1, msgs.size());
        assertTrue(msgs.get(0).get("content").endsWith("[已截断]"));
    }

    @Test
    void toolCallRecordIsFedBackAsAssistant() {
        ContextAssembler assembler = new ContextAssembler(20, 500);
        List<ChatMessage> history = List.of(
                ChatMessage.user("算一下 1+1"),
                ChatMessage.toolCall("{\"action\":\"call_tool\",\"tool_name\":\"calculator\"}"),
                ChatMessage.toolResult("[工具结果] calculator => 2"),
                ChatMessage.finalAnswer("等于 2"));
        List<Map<String, String>> msgs = assembler.buildHistoryMessages(history);
        assertEquals(4, msgs.size());
        assertEquals("assistant", msgs.get(1).get("role"));
        assertTrue(msgs.get(2).get("content").contains("calculator => 2"));
    }
}
