package com.xiaohua.polaris.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TruncatingToolCallbackTests {

    @Test
    void truncatesLongResult() {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.call("in")).thenReturn("x".repeat(10));

        TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 5);

        assertThat(callback.call("in")).isEqualTo("xxxxx…(结果已截断)");
    }

    @Test
    void keepsShortAndNullResults() {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.call("short")).thenReturn("ok");
        when(delegate.call("null")).thenReturn(null);

        TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 5);

        assertThat(callback.call("short")).isEqualTo("ok");
        assertThat(callback.call("null")).isNull();
    }

    @Test
    void delegatesToolDefinition() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(delegate.getToolDefinition()).thenReturn(definition);

        assertThat(new TruncatingToolCallback(delegate, 5).getToolDefinition()).isSameAs(definition);
    }
}
