package com.xiaohua.polaris.tool;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTests {

    private static AnnotationConfigApplicationContext context;
    private static ToolRegistry registry;

    @BeforeAll
    static void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(ToolRegistry.class, TimeTools.class, CalculatorTools.class, FakeTools.class);
        context.refresh();
        registry = context.getBean(ToolRegistry.class);
    }

    @AfterAll
    static void tearDown() {
        context.close();
    }

    @Test
    void loadsAllGroupsWhenNoGroupSelected() {
        assertThat(names(registry.callbacks(List.of())))
                .containsExactlyInAnyOrder("getCurrentTime", "calculate", "echo");
    }

    @Test
    void filtersByGroup() {
        assertThat(names(registry.callbacks(List.of("time")))).containsExactly("getCurrentTime");
        assertThat(names(registry.callbacks(List.of("time", "calc", "time"))))
                .containsExactly("getCurrentTime", "calculate");
    }

    @Test
    void unknownGroupFailsFast() {
        assertThatThrownBy(() -> registry.callbacks(List.of("nope")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void wrapsEveryCallbackWithTruncation() {
        assertThat(registry.callbacks(List.of())).allMatch(callback -> callback instanceof TruncatingToolCallback);
    }

    private static List<String> names(List<ToolCallback> callbacks) {
        return callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList();
    }

    @ToolGroup(group = "fake")
    static class FakeTools {

        @Tool(name = "echo", description = "回显文本")
        public String echo(String text) {
            return text;
        }
    }
}
