package com.minimal.agent.runtime;

import com.minimal.agent.context.ContextAssembler;
import com.minimal.agent.session.InMemorySessionStore;
import com.minimal.agent.support.FakeLlmClient;
import com.minimal.agent.tool.ToolRegistry;
import com.minimal.agent.tool.impl.CalculatorTool;
import com.minimal.agent.tool.impl.MockSearchTool;
import com.minimal.agent.tool.impl.TodoTool;
import com.minimal.agent.tool.impl.WeatherTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 主循环集成测试：用脚本化 Fake LLM 走通全部分支。
 * 对应笔试测试用例要求：直接回复 / 单工具 / 多工具链 / 追问 / session 隔离 /
 * 最大轮次 / 解析失败自纠 / 工具异常 / LLM 异常。
 */
class AgentRuntimeTest {

    private FakeLlmClient llm;
    private AgentRuntime runtime;

    @BeforeEach
    void setUp() {
        llm = new FakeLlmClient();
        ToolRegistry registry = new ToolRegistry(List.of(
                new CalculatorTool(), new MockSearchTool(), new TodoTool(), new WeatherTool()));
        SystemPromptBuilder promptBuilder = new SystemPromptBuilder(registry);
        runtime = new AgentRuntime(llm, new OutputParser(), registry,
                new InMemorySessionStore(Duration.ofMinutes(30)),
                new ContextAssembler(20, 500), promptBuilder, 5);
    }

    @Test
    void directAnswerWithoutTool() {
        llm.respond("{\"thought\":\"问候无需工具\",\"action\":\"final_answer\",\"final_answer\":\"你好！有什么可以帮你？\"}");
        AgentReply reply = runtime.chat("s1", "你好");
        assertEquals("你好！有什么可以帮你？", reply.answer());
        assertEquals(1, reply.rounds());
        assertEquals(1, llm.callCount());
    }

    @Test
    void calculatorToolCallThenAnswer() {
        llm.respond("{\"thought\":\"需要计算\",\"action\":\"call_tool\",\"tool_name\":\"calculator\","
                + "\"tool_args\":{\"expression\":\"(12+34)*5\"}}")
           .respond("{\"thought\":\"算出来了\",\"action\":\"final_answer\",\"final_answer\":\"结果是 230\"}");
        AgentReply reply = runtime.chat("s1", "帮我算 (12+34)*5");
        assertEquals("结果是 230", reply.answer());
        assertEquals(2, reply.rounds());
        // 第二轮请求里应带工具结果
        boolean sawToolResult = llm.receivedAt(2).stream()
                .anyMatch(m -> m.get("content").contains("calculator => 230"));
        assertTrue(sawToolResult, "第二轮上下文应包含 calculator 的工具结果");
    }

    @Test
    void multiToolChainWeatherThenTodo() {
        llm.respond("{\"thought\":\"先查天气\",\"action\":\"call_tool\",\"tool_name\":\"weather\",\"tool_args\":{\"city\":\"广州\"}}")
           .respond("{\"thought\":\"再记待办\",\"action\":\"call_tool\",\"tool_name\":\"todo\",\"tool_args\":{\"action\":\"add\",\"item\":\"带伞\"}}")
           .respond("{\"thought\":\"都完成了\",\"action\":\"final_answer\",\"final_answer\":\"广州天气已查，已记下带伞\"}");
        AgentReply reply = runtime.chat("s1", "查下广州天气，提醒我带伞");
        assertEquals(3, reply.rounds());
        assertEquals("广州天气已查，已记下带伞", reply.answer());
    }

    @Test
    void followUpUsesHistory() {
        // 第一轮：计算
        llm.respond("{\"thought\":\"t\",\"action\":\"call_tool\",\"tool_name\":\"calculator\",\"tool_args\":{\"expression\":\"1+1\"}}")
           .respond("{\"thought\":\"t\",\"action\":\"final_answer\",\"final_answer\":\"等于 2\"}");
        runtime.chat("s1", "1+1 等于几");
        // 第二轮：纯对话追问，脚本答复引用上文
        llm.respond("{\"thought\":\"用户追问，基于上一轮结果再乘10\",\"action\":\"call_tool\",\"tool_name\":\"calculator\",\"tool_args\":{\"expression\":\"2*10\"}}")
           .respond("{\"thought\":\"t\",\"action\":\"final_answer\",\"final_answer\":\"再乘以 10 等于 20\"}");
        AgentReply reply = runtime.chat("s1", "再乘以 10 呢");
        assertEquals("再乘以 10 等于 20", reply.answer());
        // 追问这轮的首条 LLM 请求，应携带上一轮的问答历史
        List<Map<String, String>> firstCallOfSecondRound = llm.receivedAt(3);
        boolean sawHistory = firstCallOfSecondRound.stream()
                .anyMatch(m -> m.get("content").contains("1+1 等于几"))
                && firstCallOfSecondRound.stream()
                .anyMatch(m -> m.get("content").equals("等于 2"));
        assertTrue(sawHistory, "追问时应召回上一轮对话历史");
    }

    @Test
    void todoIsolationBetweenTwoWindows() {
        // 窗口1：记"买牛奶"
        llm.respond("{\"thought\":\"t\",\"action\":\"call_tool\",\"tool_name\":\"todo\",\"tool_args\":{\"action\":\"add\",\"item\":\"买牛奶\"}}")
           .respond("{\"thought\":\"t\",\"action\":\"final_answer\",\"final_answer\":\"已记下买牛奶\"}");
        runtime.chat("userA-window1", "帮我记个待办：买牛奶");
        // 窗口2：查看待办，不应看到"买牛奶"
        llm.respond("{\"thought\":\"t\",\"action\":\"call_tool\",\"tool_name\":\"todo\",\"tool_args\":{\"action\":\"list\"}}")
           .respond("{\"thought\":\"t\",\"action\":\"final_answer\",\"final_answer\":\"当前没有待办\"}");
        AgentReply reply = runtime.chat("userA-window2", "我有哪些待办？");
        // 验证窗口2收到的工具结果里没有窗口1的待办
        boolean sawMilk = llm.receivedAt(4).stream()
                .anyMatch(m -> m.get("content").contains("买牛奶"));
        org.junit.jupiter.api.Assertions.assertFalse(sawMilk, "窗口2 不应召回窗口1 的待办");
        assertEquals("当前没有待办", reply.answer());
    }

    @Test
    void maxLoopRoundsTerminates() {
        // 模型一直要求调工具，应在第 5 轮强制结束
        for (int i = 0; i < 5; i++) {
            llm.respond("{\"thought\":\"继续\",\"action\":\"call_tool\",\"tool_name\":\"search\",\"tool_args\":{\"query\":\"agent\"}}");
        }
        AgentReply reply = runtime.chat("s1", "一直查");
        assertEquals(5, reply.rounds());
        assertTrue(reply.answer().contains("上限"), "应返回达到轮次上限的兜底答复");
        assertEquals(5, llm.callCount(), "达到最大轮次后不应再调用 LLM");
    }

    @Test
    void parseErrorTriggersSelfCorrection() {
        llm.respond("这不是 JSON，是模型胡言乱语")
           .respond("{\"thought\":\"重新按协议输出\",\"action\":\"final_answer\",\"final_answer\":\"纠正后的答复\"}");
        AgentReply reply = runtime.chat("s1", "你好");
        assertEquals("纠正后的答复", reply.answer());
        // 第二次调用应带解析失败提示
        boolean sawHint = llm.receivedAt(2).stream()
                .anyMatch(m -> m.get("content").contains("无法解析"));
        assertTrue(sawHint);
    }

    @Test
    void toolErrorIsFedBackToModel() {
        llm.respond("{\"thought\":\"t\",\"action\":\"call_tool\",\"tool_name\":\"calculator\",\"tool_args\":{\"expression\":\"1/0\"}}")
           .respond("{\"thought\":\"工具报错了，如实告知\",\"action\":\"final_answer\",\"final_answer\":\"除数不能为 0，请换个表达式\"}");
        AgentReply reply = runtime.chat("s1", "算 1/0");
        assertEquals("除数不能为 0，请换个表达式", reply.answer());
        boolean sawError = llm.receivedAt(2).stream()
                .anyMatch(m -> m.get("content").contains("ERROR"));
        assertTrue(sawError, "工具错误应回灌给模型");
    }

    @Test
    void unknownToolIsHandledGracefully() {
        llm.respond("{\"thought\":\"t\",\"action\":\"call_tool\",\"tool_name\":\"fly\",\"tool_args\":{}}")
           .respond("{\"thought\":\"t\",\"action\":\"final_answer\",\"final_answer\":\"我没有这个能力\"}");
        AgentReply reply = runtime.chat("s1", "带我飞");
        assertEquals("我没有这个能力", reply.answer());
    }

    @Test
    void llmExceptionReturnsFriendlyFallback() {
        llm.fail("连接超时");
        AgentReply reply = runtime.chat("s1", "你好");
        assertTrue(reply.answer().contains("暂时不可用"));
        boolean traced = reply.trace().stream().anyMatch(e -> e.type().equals("llm_error"));
        assertTrue(traced, "trace 应记录 LLM 异常");
    }
}
