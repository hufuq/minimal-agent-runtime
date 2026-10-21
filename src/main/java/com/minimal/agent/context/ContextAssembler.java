package com.minimal.agent.context;

import com.minimal.agent.config.AgentProperties;
import com.minimal.agent.session.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 上下文组装器：决定"哪些信息塞进送给 LLM 的窗口"，是 memory 放置策略的核心。
 *
 * 放置规则：
 * - USER / TOOL_CALL / TOOL_RESULT / FINAL_ANSWER → 回灌；
 * - ASSISTANT_THOUGHT → 不回灌（思考过程只落存储与 trace，见 README 说明）。
 *
 * 压缩规则（题目只要求基础压缩，这里实现 FIFO 截断 + 单条截断）：
 * - 消息总数超过 maxContextMessages 时，丢弃最早的历史，保留最近 N 条；
 * - 单条 TOOL_RESULT 超过 toolResultMaxChars 时截断，防止一次工具返回撑爆窗口。
 */
@Component
public class ContextAssembler {

    private final int maxMessages;
    private final int toolResultMaxChars;

    @Autowired
    public ContextAssembler(AgentProperties props) {
        this.maxMessages = props.getMaxContextMessages();
        this.toolResultMaxChars = props.getToolResultMaxChars();
    }

    /** 测试专用 */
    public ContextAssembler(int maxMessages, int toolResultMaxChars) {
        this.maxMessages = maxMessages;
        this.toolResultMaxChars = toolResultMaxChars;
    }

    /**
     * 把 session 历史转成 OpenAI 风格消息列表（不含 system prompt，由调用方加在头部）。
     */
    public List<Map<String, String>> buildHistoryMessages(List<ChatMessage> history) {
        List<ChatMessage> window = truncate(history);
        List<Map<String, String>> out = new ArrayList<>(window.size());
        for (ChatMessage m : window) {
            switch (m.type()) {
                case USER -> out.add(msg("user", m.content()));
                case FINAL_ANSWER -> out.add(msg("assistant", m.content()));
                case TOOL_CALL ->
                    // 模型自己的协议输出原样回灌，让它记得"我刚才决定调这个工具"
                    out.add(msg("assistant", m.content()));
                case TOOL_RESULT ->
                        out.add(msg("user", "[工具结果] " + truncateChars(m.content(), toolResultMaxChars)));
                case ASSISTANT_THOUGHT -> {
                    // 思考过程不回灌：节省 token，避免上下文膨胀
                }
            }
        }
        return out;
    }

    /** FIFO 截断：保留最近 maxMessages 条 */
    List<ChatMessage> truncate(List<ChatMessage> history) {
        if (history.size() <= maxMessages) {
            return history;
        }
        return new ArrayList<>(history.subList(history.size() - maxMessages, history.size()));
    }

    private String truncateChars(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…[已截断]";
    }

    private Map<String, String> msg(String role, String content) {
        return Map.of("role", role, "content", content);
    }
}
