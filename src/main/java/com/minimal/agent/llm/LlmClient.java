package com.minimal.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 客户端抽象。Agent Runtime 只依赖此接口，
 * 便于测试时替换为 Fake 实现、生产切换不同模型供应商。
 */
public interface LlmClient {

    /**
     * 发起一轮对话。
     *
     * @param messages OpenAI 风格消息列表：[{"role":"system|user|assistant","content":"..."}]
     * @return 模型输出的纯文本内容
     * @throws LlmException 网络异常、超时、鉴权失败、限流等
     */
    String chat(List<Map<String, String>> messages) throws LlmException;
}
