package com.minimal.agent.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySessionStoreTest {

    @Test
    void sameIdReturnsSameSessionDifferentIdsIsolated() {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        Session w1 = store.getOrCreate("userA-window1");
        Session w2 = store.getOrCreate("userA-window2");

        w1.append(ChatMessage.user("查一下广州天气，记个待办"));
        w2.append(ChatMessage.user("帮我写周报，记个待办"));

        assertSame(w1, store.getOrCreate("userA-window1"), "同 id 续聊应是同一会话");
        assertNotSame(w1, w2);
        assertEquals(1, store.getOrCreate("userA-window1").snapshot().size());
        assertEquals(1, store.getOrCreate("userA-window2").snapshot().size());
        assertEquals("查一下广州天气，记个待办",
                store.getOrCreate("userA-window1").snapshot().get(0).content());
        assertEquals("帮我写周报，记个待办",
                store.getOrCreate("userA-window2").snapshot().get(0).content());
    }

    @Test
    void expiredSessionIsEvictedLazily() throws InterruptedException {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMillis(80));
        store.getOrCreate("s1").append(ChatMessage.user("hi"));
        assertEquals(1, store.size());
        Thread.sleep(120);
        assertTrue(store.find("s1").isEmpty(), "过期会话读不到");
        // 过期后 getOrCreate 应返回全新空会话
        assertEquals(0, store.getOrCreate("s1").snapshot().size());
    }

    @Test
    void evictExpiredCleansUp() throws InterruptedException {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMillis(80));
        store.getOrCreate("a");
        store.getOrCreate("b");
        Thread.sleep(120);
        assertEquals(2, store.evictExpired());
        assertEquals(0, store.size());
    }
}
