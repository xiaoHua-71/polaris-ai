package com.xiaohua.polaris.chat;

public sealed interface ChatStreamEvent
        permits ChatStreamEvent.Delta, ChatStreamEvent.Done, ChatStreamEvent.Error {

    record Delta(String content) implements ChatStreamEvent {
    }

    record Done(String conversationId) implements ChatStreamEvent {
    }

    record Error(String message) implements ChatStreamEvent {
    }
}
