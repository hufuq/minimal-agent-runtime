package com.minimal.agent.controller;

import com.minimal.agent.runtime.AgentReply;
import com.minimal.agent.runtime.AgentRuntime;
import com.minimal.agent.session.ChatMessage;
import com.minimal.agent.session.SessionStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentRuntime runtime;
    private final SessionStore sessions;

    public AgentController(AgentRuntime runtime, SessionStore sessions) {
        this.runtime = runtime;
        this.sessions = sessions;
    }

    public record ChatRequest(@NotBlank(message = "sessionId 不能为空") String sessionId,
                              @NotBlank(message = "message 不能为空") String message) {
    }

    /**
     * 与 Agent 对话。同一窗口传相同 sessionId 即可续聊；
     * 不同窗口传不同 sessionId，会话完全隔离。
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@Valid @RequestBody ChatRequest req) {
        AgentReply reply = runtime.chat(req.sessionId(), req.message());
        return Map.of(
                "sessionId", req.sessionId(),
                "answer", reply.answer(),
                "rounds", reply.rounds(),
                "trace", reply.trace());
    }

    /** 查看某会话的完整消息历史（含思考与工具记录），用于演示 memory 召回与隔离 */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessage>> messages(@PathVariable String sessionId) {
        return sessions.find(sessionId)
                .map(s -> ResponseEntity.ok(s.snapshot()))
                .orElse(ResponseEntity.notFound().build());
    }
}
