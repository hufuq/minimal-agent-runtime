package com.minimal.agent.runtime;

import com.minimal.agent.config.AgentProperties;
import com.minimal.agent.context.ContextAssembler;
import com.minimal.agent.llm.LlmClient;
import com.minimal.agent.llm.LlmException;
import com.minimal.agent.session.ChatMessage;
import com.minimal.agent.session.Session;
import com.minimal.agent.session.SessionStore;
import com.minimal.agent.tool.ToolContext;
import com.minimal.agent.tool.ToolRegistry;
import com.minimal.agent.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 主循环 Runtime（全部手写，不依赖任何 Agent 框架）。
 *
 * Loop：
 *   1. 读取 session 历史，组装上下文（system prompt + 截断后的历史 + 本轮输入）
 *   2. 请求 LLM，自写解析器解析输出
 *   3. final_answer → 写回 session，结束返回
 *      call_tool   → 校验工具与参数 → 执行 → 结果写回 session 与上下文 → 继续循环
 *   4. 达到最大轮次仍未 final_answer → 兜底答复，强制结束
 *
 * 异常策略：LLM 异常直接返回友好兜底；解析失败给模型一次带错误提示的重试；
 * 工具异常封装为 ERROR 结果回灌给模型，由模型决策（重试/换方案/告知用户）。
 */
@Component
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final LlmClient llm;
    private final OutputParser parser;
    private final ToolRegistry tools;
    private final SessionStore sessions;
    private final ContextAssembler contextAssembler;
    private final SystemPromptBuilder promptBuilder;
    private final int maxRounds;

    @Autowired
    public AgentRuntime(LlmClient llm, OutputParser parser, ToolRegistry tools,
                        SessionStore sessions, ContextAssembler contextAssembler,
                        SystemPromptBuilder promptBuilder, AgentProperties props) {
        this.llm = llm;
        this.parser = parser;
        this.tools = tools;
        this.sessions = sessions;
        this.contextAssembler = contextAssembler;
        this.promptBuilder = promptBuilder;
        this.maxRounds = props.getMaxLoopRounds();
    }

    /** 测试专用：自定义最大轮次 */
    public AgentRuntime(LlmClient llm, OutputParser parser, ToolRegistry tools,
                        SessionStore sessions, ContextAssembler contextAssembler,
                        SystemPromptBuilder promptBuilder, int maxRounds) {
        this.llm = llm;
        this.parser = parser;
        this.tools = tools;
        this.sessions = sessions;
        this.contextAssembler = contextAssembler;
        this.promptBuilder = promptBuilder;
        this.maxRounds = maxRounds;
    }

    public AgentReply chat(String sessionId, String userInput) {
        Session session = sessions.getOrCreate(sessionId);
        session.append(ChatMessage.user(userInput));

        List<TraceEvent> trace = new ArrayList<>();
        String systemPrompt = promptBuilder.build();

        for (int round = 1; round <= maxRounds; round++) {
            // ---- Step 1: 组装上下文（召回 session 记忆） ----
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.addAll(contextAssembler.buildHistoryMessages(session.snapshot()));

            // ---- Step 2: 请求 LLM ----
            String raw;
            long start = System.currentTimeMillis();
            try {
                raw = llm.chat(messages);
            } catch (LlmException e) {
                log.warn("session={} round={} LLM 调用失败: {}", sessionId, round, e.getMessage());
                trace.add(TraceEvent.of(round, "llm_error", e.getMessage()));
                return finish(session, trace, round - 1,
                        "抱歉，模型服务暂时不可用（" + e.getMessage() + "），请稍后重试。");
            }
            long latency = System.currentTimeMillis() - start;
            log.info("session={} round={} llm_raw={}", sessionId, round, abbreviate(raw, 300));
            trace.add(TraceEvent.of(round, "llm_output", abbreviate(raw, 200), latency));

            // ---- Step 3: 解析输出，判断直接回复还是调用工具 ----
            AgentAction action;
            try {
                action = parser.parse(raw);
            } catch (OutputParseException e) {
                // 解析失败：把错误提示写回会话，给模型一次自我纠正的机会
                log.warn("session={} round={} 输出解析失败: {}", sessionId, round, e.getMessage());
                trace.add(TraceEvent.of(round, "parse_error", e.getMessage()));
                session.append(ChatMessage.toolResult(
                        "[系统提示] 你的上一次输出无法解析：" + e.getMessage()
                                + "。请严格按协议重新输出一个 JSON 对象。"));
                continue;
            }

            if (action.thought() != null && !action.thought().isBlank()) {
                session.append(ChatMessage.thought(action.thought()));
                trace.add(TraceEvent.of(round, "thought", abbreviate(action.thought(), 200)));
            }

            if (action.type() == AgentAction.Type.FINAL_ANSWER) {
                trace.add(TraceEvent.of(round, "final_answer", abbreviate(action.finalAnswer(), 200)));
                return finish(session, trace, round, action.finalAnswer());
            }

            // ---- Step 4: 执行工具，结果回灌，进入下一轮 ----
            session.append(ChatMessage.toolCall(action.raw()));
            ToolResult result = executeTool(sessionId, action, trace, round);
            session.append(ChatMessage.toolResult(
                    "[工具结果] " + action.toolName() + " => " + result.content()));
            trace.add(TraceEvent.of(round, "tool_result",
                    action.toolName() + " => " + abbreviate(result.content(), 200)));
        }

        // ---- 达到最大轮次，强制结束，防止死循环 ----
        log.warn("session={} 达到最大轮次 {}", sessionId, maxRounds);
        trace.add(TraceEvent.of(maxRounds, "max_rounds_reached", "强制结束循环"));
        return finish(session, trace, maxRounds,
                "抱歉，这个问题需要的处理步骤超出了我的单次上限（" + maxRounds + " 轮），"
                        + "已停止继续调用工具。可以把问题拆小一点，或补充更明确的信息后再问我。");
    }

    private ToolResult executeTool(String sessionId, AgentAction action,
                                   List<TraceEvent> trace, int round) {
        var toolOpt = tools.find(action.toolName());
        if (toolOpt.isEmpty()) {
            trace.add(TraceEvent.of(round, "tool_error", "工具不存在: " + action.toolName()));
            return ToolResult.error("工具 '" + action.toolName() + "' 不存在，"
                    + "可用工具见 system prompt 清单，或改用 final_answer 回复");
        }
        long start = System.currentTimeMillis();
        try {
            ToolResult r = toolOpt.get().execute(action.toolArgs(), new ToolContext(sessionId));
            trace.add(TraceEvent.of(round, "tool_call",
                    action.toolName() + " args=" + abbreviate(String.valueOf(action.toolArgs()), 150),
                    System.currentTimeMillis() - start));
            return r;
        } catch (Exception e) {
            // 工具实现本应自捕获异常，这里是最后一道防线
            log.error("session={} round={} 工具 {} 抛出未捕获异常", sessionId, round, action.toolName(), e);
            trace.add(TraceEvent.of(round, "tool_error",
                    action.toolName() + " 未捕获异常: " + e.getMessage()));
            return ToolResult.error("工具执行内部错误: " + e.getMessage());
        }
    }

    /** 写入最终答复到 session（memory 落库时机）并组装返回值 */
    private AgentReply finish(Session session, List<TraceEvent> trace, int rounds, String answer) {
        session.append(ChatMessage.finalAnswer(answer));
        return new AgentReply(answer, Math.max(rounds, 1), trace);
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
