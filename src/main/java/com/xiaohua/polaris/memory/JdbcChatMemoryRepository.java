package com.xiaohua.polaris.memory;

import java.util.List;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT conversation_id FROM chat_message", String.class);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbcTemplate.query(
                "SELECT message_type, content FROM chat_message WHERE conversation_id = ? ORDER BY id",
                (rs, rowNum) -> toMessage(rs.getString("message_type"), rs.getString("content")),
                conversationId);
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        jdbcTemplate.update("DELETE FROM chat_message WHERE conversation_id = ?", conversationId);
        if (messages.isEmpty()) {
            return;
        }
        List<Object[]> rows = messages.stream()
                .map(message -> new Object[] {conversationId, message.getMessageType().name(), textOf(message)})
                .toList();
        jdbcTemplate.batchUpdate(
                "INSERT INTO chat_message (conversation_id, message_type, content) VALUES (?, ?, ?)",
                rows);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update("DELETE FROM chat_message WHERE conversation_id = ?", conversationId);
    }

    static Message toMessage(String type, String content) {
        MessageType messageType = MessageType.valueOf(type);
        return switch (messageType) {
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            case SYSTEM -> new SystemMessage(content);
            case TOOL -> throw new IllegalStateException("工具消息暂不支持持久化，M5 再实现");
        };
    }

    private static String textOf(Message message) {
        return message.getText() == null ? "" : message.getText();
    }
}
