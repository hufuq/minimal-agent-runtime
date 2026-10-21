package com.minimal.agent.runtime;

/** 一次请求内的一条执行轨迹，最终随响应返回并写入日志，用于排障与复盘。 */
public record TraceEvent(int round, String type, String detail, long latencyMs) {

    public static TraceEvent of(int round, String type, String detail) {
        return new TraceEvent(round, type, detail, 0L);
    }

    public static TraceEvent of(int round, String type, String detail, long latencyMs) {
        return new TraceEvent(round, type, detail, latencyMs);
    }
}
