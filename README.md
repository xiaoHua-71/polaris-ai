# Polaris AI

北极星 —— 一个基于 Spring AI 的独立实验项目。

## 技术栈

| 组件 | 版本 |
|---|---|
| JDK | 21 |
| Spring Boot | 4.1.2 |
| Spring AI | 2.0.2（BOM 已引入，模型 starter 暂未启用） |
| 构建 | Maven |

## 启动

本机默认 JDK 是 11，需要先切换：

```bash
export JAVA_HOME=/e/Java/jdk-21.0.5
mvn spring-boot:run
```

服务端口 `8103`，上下文路径 `/api`。

## 验证

```bash
curl http://localhost:8103/api/hello
# {"app":"polaris-ai","message":"Hello, Polaris!"}
```

## 目录

```
dev/polaris-ai
├── pom.xml
└── src
    ├── main
    │   ├── java/com/xiaohua/polaris
    │   │   ├── PolarisApplication.java
    │   │   └── controller/HelloController.java
    │   └── resources/application.yml
    └── test/java/com/xiaohua/polaris
        └── PolarisApplicationTests.java
```

## 下一步

- 在 `pom.xml` 中启用 `spring-ai-starter-model-openai`（已注释）
- 通过 OpenAI 兼容协议接入 DeepSeek：`spring.ai.openai.base-url=https://api.deepseek.com`
- API key 走环境变量 `DEEPSEEK_API_KEY`，不要写进配置文件
