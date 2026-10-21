package com.minimal.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 运行参数，全部支持环境变量覆盖，见 application.yml。
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private final Llm llm = new Llm();

    /** 单次用户请求内允许的最大工具调用循环轮次，防止 Agent 死循环 */
    private int maxLoopRounds = 5;

    /** 送入 LLM 的历史消息最大条数，超出按 FIFO 丢弃最早的 */
    private int maxContextMessages = 20;

    /** session 闲置过期时间（分钟） */
    private long sessionTtlMinutes = 120;

    /** 单条工具结果写入上下文时的最大字符数 */
    private int toolResultMaxChars = 500;

    public Llm getLlm() {
        return llm;
    }

    public int getMaxLoopRounds() {
        return maxLoopRounds;
    }

    public void setMaxLoopRounds(int maxLoopRounds) {
        this.maxLoopRounds = maxLoopRounds;
    }

    public int getMaxContextMessages() {
        return maxContextMessages;
    }

    public void setMaxContextMessages(int maxContextMessages) {
        this.maxContextMessages = maxContextMessages;
    }

    public long getSessionTtlMinutes() {
        return sessionTtlMinutes;
    }

    public void setSessionTtlMinutes(long sessionTtlMinutes) {
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    public int getToolResultMaxChars() {
        return toolResultMaxChars;
    }

    public void setToolResultMaxChars(int toolResultMaxChars) {
        this.toolResultMaxChars = toolResultMaxChars;
    }

    public static class Llm {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-chat";
        private int timeoutSeconds = 30;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
