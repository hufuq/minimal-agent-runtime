package com.minimal.agent.runtime;

/** LLM 输出不符合约定 JSON 协议时抛出，由 Runtime 决定重试还是兜底。 */
public class OutputParseException extends RuntimeException {

    public OutputParseException(String message) {
        super(message);
    }
}
