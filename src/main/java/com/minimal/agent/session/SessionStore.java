package com.minimal.agent.session;

import java.util.List;
import java.util.Optional;

/**
 * Session 存储抽象。当前默认内存实现（InMemorySessionStore）；
 * 生产切换 Redis 时新建 RedisSessionStore 实现本接口即可，
 * key 设计：agent:session:{sessionId}，value 为序列化消息列表，EXPIRE 与 TTL 对齐，
 * Agent 主流程零改动。
 */
public interface SessionStore {

    /** 不存在则创建空会话 */
    Session getOrCreate(String sessionId);

    Optional<Session> find(String sessionId);

    /** 清理闲置超过 TTL 的会话，返回清理数量 */
    int evictExpired();

    /** 当前存活会话数（监控/测试用） */
    int size();

    /** 列出全部会话 id（调试用，生产环境应移除或加权限） */
    List<String> listSessionIds();
}
