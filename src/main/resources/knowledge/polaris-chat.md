# Polaris 对话链路

Polaris 是一个基于 Spring AI 2.0.2 和 Spring Boot 4.1.2 的实验项目，使用 DeepSeek 的 OpenAI 兼容接口作为对话模型，默认模型是 deepseek-chat。

## 多轮记忆

对话记忆使用 conversationId 区分会话。记忆窗口由 Spring AI 的 MessageWindowChatMemory 管理，默认最多保留 20 条消息。消息持久化到 MySQL 8 的 chat_message 表，字段包括 id、conversation_id、message_type、content 和 created_at。重启后端后，同一个 conversationId 仍然能读到历史消息。

## 流式输出

流式接口 POST /api/chat/stream 使用 SSE，Content-Type 是 text/event-stream。事件有三种：delta 表示增量文本，data 是 {"content":"..."}；done 表示正常结束，data 是 {"conversationId":"..."}；error 表示模型调用失败，data 是 {"message":"..."}。客户端断开后，服务端取消上游模型订阅。

## 结构化输出与提示词

结构化抽取接口 POST /api/chat/extract 使用 BeanOutputConverter 把模型输出转成 ExtractedPerson。模型输出无法解析或所有字段为空时返回 HTTP 422。提示词模板放在 src/main/resources/prompts 目录，扩展名是 .st，使用 Spring AI 的 StTemplateRenderer，占位符用尖括号，例如 &lt;text&gt; 和 &lt;format&gt;。
