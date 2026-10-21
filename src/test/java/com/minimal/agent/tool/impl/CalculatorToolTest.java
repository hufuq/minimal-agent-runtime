package com.minimal.agent.tool.impl;

import com.minimal.agent.tool.ToolContext;
import com.minimal.agent.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorToolTest {

    private final CalculatorTool tool = new CalculatorTool();
    private final ToolContext ctx = new ToolContext("s1");

    @Test
    void basicArithmetic() {
        assertEquals("230", eval("(12+34)*5"));
        assertEquals("3.5", eval("7/2"));
        assertEquals("14", eval("2+3*4"));
        assertEquals("-6", eval("-2*3"));
    }

    @Test
    void divisionByZeroReturnsError() {
        ToolResult r = tool.execute(Map.of("expression", "1/0"), ctx);
        assertFalse(r.success());
        assertTrue(r.content().contains("除数为 0"));
    }

    @Test
    void invalidExpressionReturnsError() {
        ToolResult r = tool.execute(Map.of("expression", "1+*2"), ctx);
        assertFalse(r.success());
    }

    @Test
    void blankExpressionReturnsError() {
        assertFalse(tool.execute(Map.of("expression", "  "), ctx).success());
    }

    private String eval(String expr) {
        ToolResult r = tool.execute(Map.of("expression", expr), ctx);
        assertTrue(r.success(), r.content());
        return r.content();
    }
}
