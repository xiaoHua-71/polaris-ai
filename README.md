# Polaris AI

北极星 —— 一个基于 **Spring AI** 的个人实验项目。目标是走通「对话 → 记忆 → 流式 → 结构化输出 → 工具调用 → RAG → MCP」全链路，并与 LangChain4j 项目 star-knowledge-assistant 形成对照。

## 技术栈

| 组件 | 版本 |
|---|---|
| JDK | 21 |
| Spring Boot | 4.1.2 |
| Spring AI | 2.0.2（OpenAI starter 已启用，OpenAI 兼容协议接入 DeepSeek） |
| 模型服务 | DeepSeek 官方，默认 `deepseek-chat` |
| 构建 | Maven |

## 启动

本机默认 JDK 是 11，需要先切换；同时确保 `DEEPSEEK_API_KEY` 在当前 shell 可见：

```bash
export JAVA_HOME=/e/Java/jdk-21.0.5
export DEEPSEEK_API_KEY=<your-key>
mvn spring-boot:run
```

服务端口 `8103`，上下文路径 `/api`。

> 没有 key 也能启动，但调用 `/api/chat` 会失败。这是 Spring AI 2.0.2 的实际行为，详见 [docs/05](docs/05-当前实现与验证.md)。

## 接口

```bash
# 连通性
curl http://localhost:8103/api/hello
# {"app":"polaris-ai","message":"Hello, Polaris!"}

# 同步对话（无记忆）
curl -X POST http://localhost:8103/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"用一句话介绍你自己"}'
# {"content":"..."}
```

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
- ⬜ M2 多轮对话与记忆（下一步）

完整计划见 [docs/03-路线图与里程碑](docs/03-路线图与里程碑.md)。
