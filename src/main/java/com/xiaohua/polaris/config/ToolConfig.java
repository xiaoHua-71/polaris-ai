package com.xiaohua.polaris.config;

import java.util.List;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xiaohua.polaris.tool.ToolRegistry;

@Configuration(proxyBeanMethods = false)
public class ToolConfig {

    @Bean
    ToolCallbackProvider toolCallbackProvider(ToolRegistry toolRegistry,
            @Value("${polaris.tools.groups:}") List<String> groups) {
        return ToolCallbackProvider.from(toolRegistry.callbacks(groups));
    }
}
