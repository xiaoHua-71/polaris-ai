package com.xiaohua.polaris.chat;

public final class ChatStreamEvent {

    private ChatStreamEvent() {
    }

    public record Delta(String content) {
    }

    public record Done(String conversationId) {
    }

    public record Error(String message) {
    }
}
