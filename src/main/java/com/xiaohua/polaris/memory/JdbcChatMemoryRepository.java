package com.xiaohua.polaris.memory;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(JdbcChatMemoryRepository.class);

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
        long start = System.nanoTime();
        List<Message> messages = jdbcTemplate.query(
                "SELECT message_type, content FROM chat_message WHERE conversation_id = ? ORDER BY id",
                (rs, rowNum) -> toMessage(rs.getString("message_type"), rs.getString("content")),
                conversationId);
        log.debug("读取记忆，conversationId={}，条数={}，耗时={}ms",
                conversationId, messages.size(), elapsedMillis(start));
        return messages;
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        long start = System.nanoTime();
        jdbcTemplate.update("DELETE FROM chat_message WHERE conversation_id = ?", conversationId);
        if (messages.isEmpty()) {
            log.debug("写入记忆，conversationId={}，条数=0，耗时={}ms", conversationId, elapsedMillis(start));
            return;
        }
        List<Object[]> rows = messages.stream()
                .map(message -> new Object[] {conversationId, message.getMessageType().name(), textOf(message)})
                .toList();
        jdbcTemplate.batchUpdate(
                "INSERT INTO chat_message (conversation_id, message_type, content) VALUES (?, ?, ?)",
                rows);
        log.debug("写入记忆，conversationId={}，条数={}，耗时={}ms",
                conversationId, messages.size(), elapsedMillis(start));
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

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
