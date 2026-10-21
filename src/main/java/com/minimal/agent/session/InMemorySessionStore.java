package com.minimal.agent.session;

import com.minimal.agent.config.AgentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 Session 存储：ConcurrentHashMap + TTL 惰性清理 + 定时清理。
 * 惰性清理：读会话时先判断是否过期，过期即删；
 * 定时清理：兜底清理长期无人访问的会话，避免内存只涨不跌。
 */
@Component
public class InMemorySessionStore implements SessionStore {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;

    @Autowired
    public InMemorySessionStore(AgentProperties props) {
        this.ttl = Duration.ofMinutes(props.getSessionTtlMinutes());
    }

    /** 测试专用：自定义 TTL */
    public InMemorySessionStore(Duration ttl) {
        this.ttl = ttl;
    }

    @Override
    public Session getOrCreate(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s != null && isExpired(s)) {
            sessions.remove(sessionId);
            s = null;
        }
        return (s != null) ? s
                : sessions.computeIfAbsent(sessionId, Session::new);
    }

    @Override
    public Optional<Session> find(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            return Optional.empty();
        }
        if (isExpired(s)) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(s);
    }

    @Override
    public int evictExpired() {
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> isExpired(e.getValue()));
        return before - sessions.size();
    }

    @Override
    public int size() {
        return sessions.size();
    }

    @Override
    public List<String> listSessionIds() {
        return List.copyOf(sessions.keySet());
    }

    private boolean isExpired(Session s) {
        return Duration.between(s.getLastActiveAt(), Instant.now()).compareTo(ttl) > 0;
    }

    /** 每 5 分钟兜底清理一次过期会话 */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    void scheduledEvict() {
        evictExpired();
    }
}
