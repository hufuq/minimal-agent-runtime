# AI Prompt 与问题解决记录

> 本文记录本次 Vibe Coding 题目中，我（候选人）如何借助 AI 完成开发：关键提问、对齐过程、以及开发中遇到的问题与解决方式。按《AI Coding 高效提问与协作指南》的五步打法组织：对齐需求 → 拆任务清单 → 按任务迭代 → 写单测 → 逐条核对。

## 一、对齐需求阶段的关键提问与决策

拿到题目后，我没有直接让 AI 写代码，而是先把题目全文 + 我的背景（Java 方向、有电商幂等/Redis 项目经验）喂给 AI，要求它复述理解并反问不确定的点。这一轮对齐产生了 4 个关键决策：

| 决策点 | AI 给出的选项 | 我的选择与理由 |
|---|---|---|
| 技术栈 | Python+FastAPI / Java+Spring Boot / 纯 Java | **Java+Spring Boot**。我是 Java 方向，主栈面试能讲透；题目未限定语言 |
| Session 存储 | 内存 / Redis / 双实现 | **内存实现 + SessionStore 接口预留 Redis**，阅卷方零依赖跑通，README 说明切换路径 |
| 解析协议 | LLM 原生 function-calling / prompt 约束 JSON 协议 | **prompt 约束 JSON 协议**。题目要求"自写解析逻辑"，且该协议即 ReAct 的 JSON 化，模型无关 |
| 工具选择 | calculator/search/read_docs 等 | 增加 **todo + weather**，因为题目 session 场景自带"查天气记待办"，测试可直接复用题目叙事 |

另一个 AI 最初建议用 Python，理由并不充分（题目原文没有"岗位优先 Python"的依据）。我纠正了这一点——**AI 的建议要过一遍自己的判断，不能直接照单全收**。

## 二、任务清单（AI 产出、我确认后开工）

1. 项目骨架（pom、配置、入口）
2. 工具层：Tool 接口 + 注册表 + 4 个工具
3. LLM 客户端（OpenAI 兼容）+ 输出解析器
4. Session 存储 + Context 管理
5. AgentRuntime 主循环 + 异常处理 + Trace + HTTP 接口
6. 测试用例
7. README / 架构题答案 / 本记录

## 三、开发中遇到的问题与解决

### 问题 1：计算器工具的 eval 注入风险

- **现象**：让 AI 实现 calculator 时，最容易的写法是调脚本引擎 eval 表达式，但 JDK 15+ 已移除 Nashorn，且 eval 有注入风险。
- **我的要求**：不接受任何 eval 方案。
- **解决**：AI 改为手写递归下降表达式解析器（expr/term/factor 三级），支持加减乘除、括号、小数、负数，除零返回 ERROR。测试覆盖 `(12+34)*5=230`、`7/2=3.5`、`1/0` 报错、非法表达式报错。

### 问题 2：LLM 输出不稳定，解析怎么兜底

- **现象**：模型有时会在 JSON 外面包 markdown 代码围栏，或前后带解释性文字。
- **解决**：OutputParser 设计三级容错——整串直解析 → 剥代码围栏 → 截取首尾花括号子串。仍失败则由 Runtime 把"输出无法解析，请按协议重新输出"写回会话，给模型一次自我纠正机会（计入轮次，防死循环）。测试用"模型胡言乱语 → 第二次纠正"的脚本验证了这条链路。

### 问题 3：定时清理过期 session 不生效

- **现象**：开发自查时发现 InMemorySessionStore 用了 `@Scheduled` 定时清理，但入口类忘了加 `@EnableScheduling`，定时任务根本不会注册。
- **解决**：入口类补上注解。这类"写了但永远不会执行"的代码，纯 review 容易漏，是在逐条核对环节抓出来的。读路径上的惰性过期清理本来就能兜住正确性，定时任务只是兜底防内存增长。

### 问题 4：思考过程（thought）要不要回灌上下文

- **权衡**：回灌能让模型看到自己之前的推理，但每轮都带 thought 会显著膨胀上下文；不回灌则节省 token。
- **决策**：thought 落 session 存储和 trace 日志（供排障复盘），**不回灌 LLM 上下文**；工具调用记录和工具结果回灌（追问的事实依据）。这个取舍写进了 README 的"memory 放置方式"章节，作为对题目"哪些信息塞入 context 更合适"的明确回答。

### 问题 5：构建环境——Maven 本地仓库权限

- **现象**：AI 执行 `mvn test` 时报 `AccessDeniedException: D:\develop\maven-repository\...`，本地仓库在 D 盘，构建沙箱无写入权限。
- **解决**：切换为非沙箱模式执行 Maven 构建。这是环境问题不是代码问题，但记录下来提醒：AI 协作时的执行环境权限也是契约的一部分。

### 问题 6：单测全过但应用启动失败——双构造函数陷阱（最有价值的一次踩坑）

- **现象**：`mvn test` 38 个测试全部通过后，我做启动冒烟测试，`mvn spring-boot:run` 却报 `BeanCreationException: No default constructor found`，`contextAssembler` bean 创建失败。
- **根因**：`ContextAssembler`、`AgentRuntime`、`InMemorySessionStore` 三个类各有两个构造函数（一个注入配置的生产构造器、一个测试专用构造器）。Spring 面对多个构造函数且没有任何一个标注 `@Autowired` 时，会退而求其次找默认无参构造器，找不到就启动失败。单测全过是因为测试都是手工 `new`，根本没走 Spring 容器——**单元测试绿 ≠ 应用能起**。
- **解决**：三个类的生产构造函数加 `@Autowired` 显式指定，重新构建（38 测试仍全过）+ 启动冒烟通过，`/agent/chat` 端到端返回预期结果。
- **教训**：这是本次开发最有代表性的一个坑——AI 生成代码时每个类孤立看都没问题，组装到容器里才暴露。所以五步打法的最后一步"逐条核对 + 真实启动验证"不能省。

## 四、逐条核对（题目要求 → 实现位置）

| 题目要求 | 实现位置 |
|---|---|
| 不依赖 Agent 框架，Runtime 手写 | `runtime/AgentRuntime.java`，全项目无 langchain/langgraph 依赖 |
| Loop 四步：输入→判断→调工具→判断继续/返回 | `AgentRuntime.chat()` 主循环 |
| 至少 3 个工具 | calculator / search / todo / weather（4 个） |
| 工具注册机制（名称+描述+Schema） | `tool/Tool.java` + `tool/ToolRegistry.java` |
| LLM 输出解析逻辑 | `runtime/OutputParser.java`（三级容错） |
| session 隔离、续聊 | `session/SessionStore` + `InMemorySessionStore`，测试 `todoIsolationBetweenTwoWindows` |
| 最大轮次限制 | `agent.max-loop-rounds`，测试 `maxLoopRoundsTerminates` |
| 多轮记忆与追问（纯对话/带工具） | 测试 `followUpUsesHistory` |
| context 基础压缩 | `ContextAssembler`：FIFO 截断 + 工具结果截断 |
| 基本异常处理 | 工具内自捕获 + Runtime 防线 + 全局异常处理，测试覆盖 LLM 异常/工具异常/未知工具 |
| trace/执行日志 | `TraceEvent` 随响应返回 + slf4j 日志 |
| 测试用例 | 38 个测试全部通过 |
| 真实 LLM API | OpenAI 兼容客户端，环境变量配置 key |
| README（运行方式/系统设计/memory 召回说明） | `README.md` 第一~三章 |

## 五、我的体会

这次协作里 AI 最大的价值不是"替我写代码"，而是三件事：一是**对齐阶段把我没想到的决策点摆到台面上**（比如解析协议选型）；二是**按任务清单稳定地产出结构化骨架**，我把精力集中在关键取舍上（thought 不回灌、calculator 禁 eval）；三是**测试用例的穷举**比我手写快得多。但每一步产出都需要我 review 纠偏——比如 Python 技术栈的建议就是错的，照单全收就会带着错误方向跑完全程。
