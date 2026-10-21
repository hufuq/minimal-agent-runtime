package com.minimal.agent.runtime;

import java.util.List;

/** Agent 一次请求的最终产出：答复 + 实际循环轮数 + 全链路执行轨迹。 */
public record AgentReply(String answer, int rounds, List<TraceEvent> trace) {
}
