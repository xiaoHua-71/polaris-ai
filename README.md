# Polaris AI

北极星 —— 一个基于 **Spring AI** 的个人实验项目。目标是走通「对话 → 记忆 → 流式 → 结构化输出 → 工具调用 → RAG → MCP」全链路，并与 LangChain4j 项目 star-knowledge-assistant 形成对照。

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
```

同一 `conversationId` 连续请求即可多轮对话；换 ID 即新会话。

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
    │   │   ├── chat/              # 对话接口
    │   │   ├── config/            # ChatClient 配置
    │   │   └── controller/        # Hello
    │   └── resources/application.yml
    └── test/java/com/xiaohua/polaris
```

## 当前状态

- ✅ M0 项目骨架
- ✅ M1 最小对话闭环（DeepSeek）
- 🚧 M2 多轮对话与记忆（MySQL 持久化）：代码与测试完成，真实 E2E 待验证
- ⬜ M3 流式输出

完整计划见 [docs/03-路线图与里程碑](docs/03-路线图与里程碑.md)。
