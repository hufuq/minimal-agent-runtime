# minimal-agent-runtime

从零实现的最小可用 Agent。**不依赖任何 Agent 框架**（LangGraph / OpenHands / LangChain Agents 均未使用），
Agent Runtime 主循环、工具注册与调度、LLM 输出解析、Session 与上下文管理全部手写。
LLM 调用仅使用 OpenAI 兼容协议的 HTTP 客户端（等价于一个模型 SDK，不含任何编排逻辑）。

技术栈：Java 17 + Spring Boot 3（仅用作 HTTP 接口层与依赖注入）+ 无其他运行时中间件依赖。

---

## 一、运行方式

### 1. 环境依赖

- JDK 17+
- Maven 3.8+
- 一个 OpenAI 兼容协议的 LLM API Key（DeepSeek / 通义 / Kimi / OpenAI 官方均可）

### 2. 配置环境变量

```bash
# Windows PowerShell
$env:LLM_API_KEY="sk-你的key"
$env:LLM_BASE_URL="https://api.deepseek.com"   # 可选，默认 DeepSeek
$env:LLM_MODEL="deepseek-chat"                 # 可选

# Linux / macOS
export LLM_API_KEY="sk-你的key"
```

未配置 `LLM_API_KEY` 时服务可正常启动、测试可正常运行，但实际对话会返回"模型服务暂时不可用"的兜底提示。

### 3. 启动

```bash
mvn spring-boot:run
```

### 4. 调用示例

```bash
# 窗口1：查天气记待办
curl -X POST http://localhost:8080/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"userA-window1","message":"查一下广州天气，帮我把带伞记到待办"}'

# 窗口2：同一用户的另一个窗口，会话完全隔离
curl -X POST http://localhost:8080/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"userA-window2","message":"帮我列一下周报大纲，记个待办：周五前交"}'

# 窗口1 续聊（传相同 sessionId 即可召回历史）
curl -X POST http://localhost:8080/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"userA-window1","message":"我都有哪些待办？"}'

# 查看某会话的完整消息记录（含思考与工具调用轨迹）
curl http://localhost:8080/agent/sessions/userA-window1/messages
```

响应结构：

```json
{
  "sessionId": "userA-window1",
  "answer": "广州今天多云，气温 26℃，已把「带伞」记入待办。",
  "rounds": 3,
  "trace": [
    {"round": 1, "type": "llm_output", "detail": "...", "latencyMs": 812},
    {"round": 1, "type": "tool_call",  "detail": "weather args={city=广州}", "latencyMs": 0},
    {"round": 1, "type": "tool_result","detail": "weather => [mock 天气] 广州：多云...", "latencyMs": 0}
  ]
}
```

### 5. 运行测试

```bash
mvn test
```

全部测试使用脚本化 Fake LLM（`src/test/java/.../support/FakeLlmClient.java`），不消耗真实 API 额度，可离线运行。

---

## 二、系统设计

### 1. 模块划分

```
controller   HTTP 接口层（/agent/chat、/agent/sessions/{id}/messages）
runtime      Agent 核心：主循环 AgentRuntime、输出解析器 OutputParser、系统提示词 SystemPromptBuilder
llm          LLM 客户端抽象 LlmClient + OpenAI 兼容实现（可替换）
tool         工具抽象 Tool、注册表 ToolRegistry、4 个内置工具
session      会话模型 Session/ChatMessage、存储抽象 SessionStore、内存实现（TTL 清理）
context      上下文组装器 ContextAssembler（决定哪些信息回灌 LLM、如何截断压缩）
config       运行参数（最大轮次、上下文窗口大小、TTL 等，全部可环境变量覆盖）
```

### 2. 主循环（Loop）

```
用户输入(sessionId, message)
   │
   ├─ 1. 加载 session 历史，组装上下文：system prompt(工具Schema+输出协议) + 截断后的历史 + 本轮输入
   │
   ├─ 2. 请求 LLM ──异常──→ 返回兜底答复"模型服务暂时不可用"，写入 trace
   │
   ├─ 3. 解析输出（OutputParser，三级容错）
   │      ├─ 解析失败 → 把错误提示写回会话，给模型一次自我纠正机会 → 回到 1
   │      ├─ final_answer → 答复写入 session → 返回用户，Loop 结束
   │      └─ call_tool → 4
   │
   ├─ 4. 工具调度：工具不存在 → ERROR 回灌；存在 → 执行（异常兜底捕获）
   │      工具调用记录与结果写入 session 与上下文 → 回到 1
   │
   └─ 达到最大轮次（默认 5）→ 强制结束，返回兜底答复，防止死循环
```

### 3. 输出协议与解析

约定模型每次输出一个 JSON 对象（在 system prompt 中强约束）：

```json
{"thought":"思考过程","action":"call_tool","tool_name":"calculator","tool_args":{"expression":"1+2"}}
{"thought":"思考过程","action":"final_answer","final_answer":"给用户的答复"}
```

`OutputParser` 三级容错解析：整串直解析 → 剥 markdown 代码围栏 → 截取首尾花括号子串。
选择 prompt 约束 JSON 协议而非 LLM 原生 function-calling，原因：题目要求"实现 LLM 输出的解析逻辑"，
且该协议即经典 ReAct 模式的 JSON 化变体，模型无关、任何兼容 OpenAI 的模型都能跑。

### 4. 工具注册机制

每个工具实现 `Tool` 接口，向注册表提供四要素：**名称、描述、入参 JSON Schema、执行函数**。
`SystemPromptBuilder` 将全部工具的 Schema 注入 system prompt，LLM 据此**自主决策**调哪个工具、如何填参。
新增工具只需新建一个 `@Component` 实现类，主流程零改动。

内置 4 个工具：

| 工具 | 说明 | 备注 |
|---|---|---|
| calculator | 加减乘除/括号/小数 | 自写递归下降解析器，不用 eval，杜绝注入 |
| search | 联网搜索 | mock 实现，关键词命中内置语料 |
| todo | 待办 add/list/done/clear | **按 sessionId 隔离**，直接覆盖题目"窗口1/窗口2 记待办"场景 |
| weather | 城市天气查询 | mock 实现，结果由城市名哈希确定性生成，便于测试断言 |

### 5. 异常处理

| 异常场景 | 策略 |
|---|---|
| LLM 超时/报错 | 返回友好兜底答复，trace 记录 `llm_error` |
| 输出解析失败 | 错误提示写回会话，给模型一次自我纠正机会，计入轮次 |
| 工具不存在 | ERROR 回灌模型，由模型改用其他工具或直接答复 |
| 参数缺失/非法 | 工具内校验并返回 ERROR，模型可修正参数重试 |
| 工具执行异常 | 工具内自捕获 + Runtime 最后防线兜底，绝不导致会话崩溃 |
| 请求参数非法 | Bean Validation + 全局异常处理，400 JSON 错误 |

---

## 三、Memory（Session）的召回时机与放置方式

这是本设计的核心问题，明确回答题目"哪些信息塞入 context 更合适"：

### 1. 召回时机

- **每轮 Loop 开始前**：从 SessionStore 读取该 sessionId 的完整历史快照，组装进上下文。
  因此同一窗口随时续聊都能记住之前的状态，支持纯对话追问与带工具的追问。
- **每条消息产生时即写入**：用户输入、模型思考、工具调用、工具结果、最终答复，
  在产生的当下就 append 进 session——即使本轮 Loop 尚未结束，中途崩溃也只丢未完成的半轮。

### 2. 放置方式：哪些回灌、哪些只落存储

| 消息类型 | 回灌 LLM 上下文？ | 理由 |
|---|---|---|
| USER 用户输入 | 是 | 对话本体 |
| FINAL_ANSWER 最终答复 | 是 | 追问的语义基础 |
| TOOL_CALL 工具调用（模型原始 JSON） | 是 | 让模型记得"我刚才决定调了这个工具"，维持带工具追问的连贯性 |
| TOOL_RESULT 工具结果 | 是（超长截断 500 字符） | 后续推理的事实依据；截断防止一次超长返回撑爆窗口 |
| ASSISTANT_THOUGHT 思考过程 | **否** | 思考是推理的中间产物，回灌只增 token 不增信息；落 session 与 trace 供排障复盘即可 |

### 3. 隔离原理

sessionId 由调用方（前端窗口）生成，同一用户的窗口1、窗口2 是两个 sessionId，
读取、写入都按 id 隔离，天然互不影响；todo 工具的内部状态也按 sessionId 分桶，待办互不串。

### 4. 过期与压缩

- **TTL**：闲置超过 `agent.session-ttl-minutes`（默认 120 分钟）的会话，读时惰性删除 + 每 5 分钟定时兜底清理。
- **基础压缩**（题目要求从简）：
  - 消息条数超过 `agent.max-context-messages`（默认 20）时，**FIFO 丢弃最早历史**，system prompt 始终保留；
  - 单条工具结果超过 `agent.tool-result-max-chars` 时截断。
  更复杂的摘要式压缩（如用 LLM 总结早期历史）未实现，属于题目明确的"复杂压缩不用做"范围。

### 5. 切换 Redis 的路径

`SessionStore` 是接口。生产实现 `RedisSessionStore`：
key `agent:session:{sessionId}`，value 为消息列表的 JSON 序列化，`EXPIRE` 与 TTL 对齐（每次写入刷新），
Agent 主流程零改动。当前默认内存实现是为了让阅卷方零依赖即可跑通全流程。

---

## 四、测试用例

`mvn test` 共 30+ 条断言，覆盖题目要求的全部功能点：

| 用例 | 验证点 |
|---|---|
| directAnswerWithoutTool | 无需工具直接回复，不进工具分支 |
| calculatorToolCallThenAnswer | calculator 工具调用，结果回灌第二轮 |
| multiToolChainWeatherThenTodo | 一次请求内多工具链式调用 |
| followUpUsesHistory | 追问时召回上一轮问答（纯对话 + 带工具） |
| todoIsolationBetweenTwoWindows | **session 隔离**：窗口2 看不到窗口1 的待办 |
| maxLoopRoundsTerminates | 模型反复调工具，达最大轮次强制结束 |
| parseErrorTriggersSelfCorrection | 模型输出乱码 → 自我纠正重试 |
| toolErrorIsFedBackToModel | 工具报错（1/0）→ ERROR 回灌 → 模型如实告知用户 |
| unknownToolIsHandledGracefully | 幻觉出不存在的工具 → 优雅处理 |
| llmExceptionReturnsFriendlyFallback | LLM 异常 → 友好兜底 + trace 记录 |
| OutputParserTest | 解析器 9 种形态（围栏/废话/非法 action/缺字段…） |
| CalculatorToolTest | 正常计算/除零/非法表达式 |
| InMemorySessionStoreTest | 同 id 同会话、异 id 隔离、TTL 惰性+主动清理 |
| ContextAssemblerTest | thought 不回灌、FIFO 截断、工具结果截断 |
| AgentControllerTest | 接口契约、参数校验 400、历史回读 |

---

## 五、配套文档

- 架构设计题答案（第 4 题：Agent 的幂等性与重试设计）：[docs/architecture-q4.md](docs/architecture-q4.md)
- AI Prompt 与问题解决记录：[docs/ai-prompt-log.md](docs/ai-prompt-log.md)
