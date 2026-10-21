package com.minimal.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimal.agent.context.ContextAssembler;
import com.minimal.agent.runtime.AgentRuntime;
import com.minimal.agent.runtime.OutputParser;
import com.minimal.agent.runtime.SystemPromptBuilder;
import com.minimal.agent.session.InMemorySessionStore;
import com.minimal.agent.support.FakeLlmClient;
import com.minimal.agent.tool.ToolRegistry;
import com.minimal.agent.tool.impl.CalculatorTool;
import com.minimal.agent.tool.impl.MockSearchTool;
import com.minimal.agent.tool.impl.TodoTool;
import com.minimal.agent.tool.impl.WeatherTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 接口层测试：standalone MockMvc，全手工组装依赖，确定性验证契约与校验。 */
class AgentControllerTest {

    private MockMvc mvc;
    private FakeLlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        llm = new FakeLlmClient();
        ToolRegistry registry = new ToolRegistry(List.of(
                new CalculatorTool(), new MockSearchTool(), new TodoTool(), new WeatherTool()));
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        AgentRuntime runtime = new AgentRuntime(llm, new OutputParser(), registry, store,
                new ContextAssembler(20, 500), new SystemPromptBuilder(registry), 5);
        mvc = MockMvcBuilders
                .standaloneSetup(new AgentController(runtime, store))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void chatEndpointReturnsAnswerAndTrace() throws Exception {
        llm.respond("{\"thought\":\"t\",\"action\":\"final_answer\",\"final_answer\":\"你好！\"}");
        mvc.perform(post("/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\",\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("你好！"))
                .andExpect(jsonPath("$.sessionId").value("s1"))
                .andExpect(jsonPath("$.rounds").value(1))
                .andExpect(jsonPath("$.trace").isArray());
    }

    @Test
    void blankSessionIdRejected() throws Exception {
        mvc.perform(post("/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"  \",\"message\":\"hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void malformedJsonRejected() throws Exception {
        mvc.perform(post("/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sessionMessagesCanBeReadBack() throws Exception {
        llm.respond("{\"thought\":\"t\",\"action\":\"final_answer\",\"final_answer\":\"收到\"}");
        mvc.perform(post("/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s-read\",\"message\":\"记一下\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/agent/sessions/s-read/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content", containsString("记一下")));
        mvc.perform(get("/agent/sessions/not-exist/messages"))
                .andExpect(status().isNotFound());
    }
}
