package com.minimal.agent.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一个独立会话窗口。sessionId 由调用方（前端窗口）生成，
 * 同一用户的不同窗口使用不同 sessionId，数据天然隔离、互不可见。
 */
public class Session {

    private final String sessionId;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Instant createdAt = Instant.now();
    private volatile Instant lastActiveAt = Instant.now();

    public Session(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    /** 追加消息并刷新活跃时间。加 synchronized 防止同会话并发写乱序 */
    public synchronized void append(ChatMessage message) {
        messages.add(message);
        lastActiveAt = Instant.now();
    }

    /** 返回消息快照，调用方修改不影响内部状态 */
    public synchronized List<ChatMessage> snapshot() {
        return new ArrayList<>(messages);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }
}
