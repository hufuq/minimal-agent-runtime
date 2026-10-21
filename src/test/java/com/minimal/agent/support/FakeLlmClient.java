package com.minimal.agent.support;

import com.minimal.agent.llm.LlmClient;
import com.minimal.agent.llm.LlmException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 测试用脚本化 LLM：按队列顺序返回预设响应，同时记录每次收到的消息列表，
 * 用于断言上下文组装、历史召回是否正确。
 */
public class FakeLlmClient implements LlmClient {

    private final Queue<Object> script = new ArrayDeque<>();
    private final List<List<Map<String, String>>> receivedMessages = new ArrayList<>();

    /** 预置一段模型输出 */
    public FakeLlmClient respond(String raw) {
        script.add(raw);
        return this;
    }

    /** 预置一次 LLM 异常 */
    public FakeLlmClient fail(String message) {
        script.add(new LlmException(message));
        return this;
    }

    @Override
    public String chat(List<Map<String, String>> messages) throws LlmException {
        receivedMessages.add(messages);
        Object next = script.poll();
        if (next == null) {
            throw new IllegalStateException("FakeLlmClient 脚本已耗尽，但又被调用了一次");
        }
        if (next instanceof LlmException e) {
            throw e;
        }
        return (String) next;
    }

    /** 第 n 次调用（从 1 开始）收到的消息列表 */
    public List<Map<String, String>> receivedAt(int n) {
        return receivedMessages.get(n - 1);
    }

    public int callCount() {
        return receivedMessages.size();
    }
}
