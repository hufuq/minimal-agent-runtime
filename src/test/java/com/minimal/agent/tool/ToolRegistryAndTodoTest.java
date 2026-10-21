package com.minimal.agent.tool;

import com.minimal.agent.tool.impl.TodoTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryAndTodoTest {

    @Test
    void registryDescribesAllToolsWithSchema() {
        TodoTool todo = new TodoTool();
        ToolRegistry registry = new ToolRegistry(List.of(todo));
        assertTrue(registry.find("todo").isPresent());
        assertTrue(registry.find("not_exists").isEmpty());

        List<Map<String, Object>> described = registry.describeAll();
        assertEquals(1, described.size());
        assertEquals("todo", described.get(0).get("name"));
        assertTrue(described.get(0).containsKey("description"));
        assertTrue(described.get(0).containsKey("parameters"));
    }

    @Test
    void duplicateToolNameRejected() {
        TodoTool todo = new TodoTool();
        ToolRegistry registry = new ToolRegistry(List.of(todo));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> registry.register(todo));
    }

    @Test
    void todoIsIsolatedBySession() {
        TodoTool todo = new TodoTool();
        ToolContext window1 = new ToolContext("session-A-窗口1");
        ToolContext window2 = new ToolContext("session-A-窗口2");

        todo.execute(Map.of("action", "add", "item", "查天气"), window1);
        todo.execute(Map.of("action", "add", "item", "写周报"), window2);

        String list1 = todo.execute(Map.of("action", "list"), window1).content();
        String list2 = todo.execute(Map.of("action", "list"), window2).content();

        assertTrue(list1.contains("查天气"));
        assertFalse(list1.contains("写周报"), "窗口1 不应看到窗口2 的待办");
        assertTrue(list2.contains("写周报"));
        assertFalse(list2.contains("查天气"), "窗口2 不应看到窗口1 的待办");
    }

    @Test
    void todoDoneValidatesIndex() {
        TodoTool todo = new TodoTool();
        ToolContext ctx = new ToolContext("s1");
        todo.execute(Map.of("action", "add", "item", "x"), ctx);
        assertFalse(todo.execute(Map.of("action", "done", "index", 5), ctx).success());
        assertFalse(todo.execute(Map.of("action", "done", "index", "abc"), ctx).success());
        assertTrue(todo.execute(Map.of("action", "done", "index", 1), ctx).success());
    }
}
