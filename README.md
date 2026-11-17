# Polaris AI

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.2-6DB33F)
![Model](https://img.shields.io/badge/Model-DeepSeek-4D6BFE)

**一个能真正跑起来的 Spring AI 全链路实战工程。**

北极星（Polaris）从一条最小对话开始，逐个打通「对话 → 记忆 → 流式输出 → 结构化输出 → 工具调用 → RAG → MCP」。每一站都留下可执行的验证命令、实测结论和踩坑记录——它不是 API 文档的搬运，而是一份回答「Spring AI 在真实工程里到底怎么写、坑在哪」的实践笔记。

- **完整链路**：ChatClient、Advisor、Memory、Tool Calling 串成一条线，不是孤立 demo
- **可复现**：每个里程碑都有验证记录，照着 README 就能在本机跑通
- **可对照**：与 LangChain4j 项目 star-knowledge-assistant 同题实现，看清两个框架的取舍
- **诚实边界**：单机实验项目，不追求生产可用；没实测过的结论一律标注「待验证」

目前已走完 M0–M5，RAG 与 MCP 在路线图上。如果你正在做 Spring AI 选型，或想找一个从 0 到 1 的 LLM 应用参考实现，可以从这里开始。

## 技术栈

| 组件 | 版本 |
|---|---|
| JDK | 21 |
| Spring Boot | 4.1.2 |
| Spring AI | 2.0.2（OpenAI starter 已启用，OpenAI 兼容协议接入 DeepSeek） |
| 模型服务 | DeepSeek 官方，默认 `deepseek-chat` |
| 数据库 | MySQL 8（多轮记忆，库 `polaris_ai`，表 `chat_message`） |
| 构建 | Maven |

## 启动

本机默认 JDK 是 11，需要先切换；同时确保 DeepSeek key 和 MySQL 密码在当前 shell 可见：

```bash
# 首次运行：建库建表（可重复执行）
mysql -u root -p < sql/schema.sql

export JAVA_HOME=/e/Java/jdk-21.0.5
export DEEPSEEK_API_KEY=<your-key>
export MYSQL_PASSWORD=<your-mysql-password>
mvn spring-boot:run
```

服务端口 `8103`，上下文路径 `/api`。MySQL 地址、库名、用户名可分别用 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` / `MYSQL_USERNAME` 覆盖，默认 `localhost:3306/polaris_ai`、`root`。

> 没有 key 也能启动，但调用 `/api/chat` 会失败；MySQL 不可用则启动失败。这是 M2 起的有意变化，详见 [docs/11](docs/11-对话记忆.md)。

## 接口

```bash
# 连通性
curl http://localhost:8103/api/hello
# {"app":"polaris-ai","message":"Hello, Polaris!"}

# 同步对话（多轮记忆，按 conversationId 隔离）
curl -X POST http://localhost:8103/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"用一句话介绍你自己","conversationId":"11111111-1111-1111-1111-111111111111"}'
# {"content":"..."}

# 流式对话（SSE：delta / done / error）
curl -N -X POST http://localhost:8103/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"message":"用一句话介绍你自己","conversationId":"11111111-1111-1111-1111-111111111111"}'
# event:delta
# data:{"content":"我"}

# 结构化抽取（无记忆；解析失败或未抽到字段 → 422）
curl -X POST http://localhost:8103/api/chat/extract \
  -H 'Content-Type: application/json' \
  -d '{"message":"Zhang San is 30 and works in Hangzhou as a backend engineer."}'
# {"name":"Zhang San","age":30,"city":"Hangzhou","occupation":"backend engineer"}

# 带工具的对话（模型自主调用 getCurrentTime / calculate）
curl -X POST http://localhost:8103/api/chat/tools \
  -H 'Content-Type: application/json' \
  -d '{"message":"What time is it in Shanghai? Also calculate (12+3)*4.","conversationId":"22222222-2222-2222-2222-222222222222"}'
# {"content":"..."}
```

同一 `conversationId` 连续请求即可多轮对话；换 ID 即新会话。两个接口共享同一套记忆。

中文请求体建议先写入 UTF-8 文件，再用 `curl --data-binary @文件`，避免 Windows 终端编码问题。

## 文档

- [docs/00-索引与阅读顺序](docs/00-索引与阅读顺序.md) —— 先读这篇
- [docs/03-路线图与里程碑](docs/03-路线图与里程碑.md) —— M0–M8 计划
- [docs/05-当前实现与验证](docs/05-当前实现与验证.md) —— 已经有什么、验证到什么程度
- [docs/06-风险与待验证](docs/06-风险与待验证.md) —— 实测事实与待验证项

## 目录

```
dev/polaris-ai
├── docs/                          # 设计文档与路线图
├── sql/                           # 建库建表脚本
├── pom.xml
└── src
    ├── main
    │   ├── java/com/xiaohua/polaris
    │   │   ├── PolarisApplication.java
    │   │   ├── chat/              # 对话 / 流式 / 结构化抽取接口
    │   │   ├── tool/              # 只读工具与结果截断
    │   │   ├── config/            # ChatClient / 工具装配
    │   │   ├── memory/            # MySQL 记忆仓库
    │   │   ├── observability/     # 链路 / prompt 日志 Advisor
    │   │   └── controller/        # Hello
    │   └── resources
    │       ├── application.yml
    │       └── prompts/           # 提示词模板（ST 语法）
    └── test/java/com/xiaohua/polaris
```

## 当前状态

- ✅ M0 项目骨架
- ✅ M1 最小对话闭环（DeepSeek）
- ✅ M2 多轮对话与记忆（MySQL 持久化，2026-11-07 验证通过）
- ✅ M3 流式输出（SSE，2026-11-07 验证通过）
- ✅ M4 结构化输出与提示词（2026-11-16 验证通过）
- ✅ M5 工具调用（2026-11-16 验证通过）

完整计划见 [docs/03-路线图与里程碑](docs/03-路线图与里程碑.md)。
