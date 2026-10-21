package com.minimal.agent.llm;

/** LLM 调用异常：网络、超时、鉴权、限流、响应结构非法等统一封装。 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
