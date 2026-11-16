package com.xiaohua.polaris.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xiaohua.polaris.tool.CalculatorTools;
import com.xiaohua.polaris.tool.TimeTools;
import com.xiaohua.polaris.tool.TruncatingToolCallback;

@Configuration(proxyBeanMethods = false)
public class ToolConfig {

    private static final int TOOL_RESULT_MAX_CHARS = 2000;

    @Bean
    TimeTools timeTools() {
        return new TimeTools();
    }

    @Bean
    CalculatorTools calculatorTools() {
        return new CalculatorTools();
    }

    @Bean
    ToolCallbackProvider toolCallbackProvider(TimeTools timeTools, CalculatorTools calculatorTools) {
        List<ToolCallback> callbacks = Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(timeTools, calculatorTools)
                        .build()
                        .getToolCallbacks())
                .map(callback -> (ToolCallback) new TruncatingToolCallback(callback, TOOL_RESULT_MAX_CHARS))
                .toList();
        return ToolCallbackProvider.from(callbacks);
    }
}
