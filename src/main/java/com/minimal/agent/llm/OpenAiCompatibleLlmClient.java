package com.minimal.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimal.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的 LLM 客户端（POST {baseUrl}/v1/chat/completions）。
 * DeepSeek / 通义 / Kimi / OpenAI 官方均兼容该协议，仅作 HTTP 客户端，
 * 不含任何 Agent 编排逻辑——编排全部在自写的 AgentRuntime 中。
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final AgentProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public OpenAiCompatibleLlmClient(AgentProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getLlm().getTimeoutSeconds()))
                .build();
    }

    @Override
    public String chat(List<Map<String, String>> messages) throws LlmException {
        AgentProperties.Llm cfg = props.getLlm();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new LlmException("未配置 LLM_API_KEY，请在环境变量中设置");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", cfg.getModel(),
                    "messages", messages,
                    "temperature", 0.2);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getBaseUrl() + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new LlmException("LLM 接口返回 " + resp.statusCode() + ": "
                        + abbreviate(resp.body()));
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new LlmException("LLM 响应缺少 choices[0].message.content: "
                        + abbreviate(resp.body()));
            }
            return content.asText();
        } catch (LlmException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM 调用被中断", e);
        } catch (Exception e) {
            throw new LlmException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
