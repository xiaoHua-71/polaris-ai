package com.xiaohua.polaris.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public final class ToolRegistry {

    private static final int TOOL_RESULT_MAX_CHARS = 2000;

    private final Map<String, List<ToolCallback>> byGroup;
    private final List<ToolCallback> all;

    public ToolRegistry(ApplicationContext applicationContext) {
        Map<String, List<ToolCallback>> grouped = new TreeMap<>();
        new TreeMap<>(applicationContext.getBeansWithAnnotation(ToolGroup.class))
                .forEach((beanName, toolObject) -> {
                    String group = applicationContext.findAnnotationOnBean(beanName, ToolGroup.class).group();
                    grouped.computeIfAbsent(group, key -> new ArrayList<>()).addAll(callbacksOf(toolObject));
                });
        this.byGroup = grouped;
        this.all = grouped.values().stream().flatMap(List::stream).toList();
    }

    public List<ToolCallback> callbacks(Collection<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return all;
        }
        List<String> unknown = groups.stream().filter(group -> !byGroup.containsKey(group)).distinct().toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("未知的工具分组：" + unknown + "，可用分组：" + byGroup.keySet());
        }
        return groups.stream().distinct().flatMap(group -> byGroup.get(group).stream()).toList();
    }

    private static List<ToolCallback> callbacksOf(Object toolObject) {
        return Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(toolObject)
                        .build()
                        .getToolCallbacks())
                .map(callback -> (ToolCallback) new TruncatingToolCallback(callback, TOOL_RESULT_MAX_CHARS))
                .toList();
    }
}
