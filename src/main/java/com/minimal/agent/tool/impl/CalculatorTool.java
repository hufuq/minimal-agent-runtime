package com.minimal.agent.tool.impl;

import com.minimal.agent.tool.Tool;
import com.minimal.agent.tool.ToolContext;
import com.minimal.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 计算器：自写递归下降表达式解析，支持 + - * / 括号与小数，
 * 不使用任何脚本引擎 eval，避免注入风险。
 */
@Component
public class CalculatorTool implements Tool {

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "数学计算工具，支持加减乘除、括号和小数，例如 (12+34)*5/2";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of(
                                "type", "string",
                                "description", "要计算的数学表达式，如 (12+34)*5")),
                "required", List.of("expression"));
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        Object expr = args.get("expression");
        if (expr == null || expr.toString().isBlank()) {
            return ToolResult.error("参数 expression 不能为空");
        }
        try {
            double value = new Parser(expr.toString()).parse();
            // 整数结果不显示小数点，阅读体验更好
            String out = (value == Math.rint(value) && !Double.isInfinite(value))
                    ? String.valueOf((long) value)
                    : String.valueOf(value);
            return ToolResult.ok(out);
        } catch (ArithmeticException | IllegalArgumentException e) {
            return ToolResult.error("表达式计算失败: " + e.getMessage());
        }
    }

    /** 递归下降解析器：expr -> term ((+|-) term)* ; term -> factor ((*|/) factor)* ; factor -> number | (expr) | -factor */
    static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        double parse() {
            double v = parseExpr();
            skipWs();
            if (pos != s.length()) {
                throw new IllegalArgumentException("存在无法识别的字符，位置 " + pos);
            }
            return v;
        }

        private double parseExpr() {
            double v = parseTerm();
            while (true) {
                skipWs();
                if (match('+')) {
                    v += parseTerm();
                } else if (match('-')) {
                    v -= parseTerm();
                } else {
                    return v;
                }
            }
        }

        private double parseTerm() {
            double v = parseFactor();
            while (true) {
                skipWs();
                if (match('*')) {
                    v *= parseFactor();
                } else if (match('/')) {
                    double d = parseFactor();
                    if (d == 0) {
                        throw new ArithmeticException("除数为 0");
                    }
                    v /= d;
                } else {
                    return v;
                }
            }
        }

        private double parseFactor() {
            skipWs();
            if (match('-')) {
                return -parseFactor();
            }
            if (match('(')) {
                double v = parseExpr();
                if (!match(')')) {
                    throw new IllegalArgumentException("缺少右括号");
                }
                return v;
            }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("期望数字，位置 " + pos);
            }
            try {
                return Double.parseDouble(s.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("数字格式错误: " + s.substring(start, pos));
            }
        }

        private boolean match(char c) {
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }
    }
}
