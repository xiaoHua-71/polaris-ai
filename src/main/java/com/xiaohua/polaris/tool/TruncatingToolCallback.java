package com.xiaohua.polaris.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.Assert;

public final class TruncatingToolCallback implements ToolCallback {

    private static final String TRUNCATED_SUFFIX = "…(结果已截断)";

    private final ToolCallback delegate;
    private final int maxChars;

    public TruncatingToolCallback(ToolCallback delegate, int maxChars) {
        Assert.notNull(delegate, "delegate 不能为空");
        Assert.isTrue(maxChars > 0, "maxChars 必须大于 0");
        this.delegate = delegate;
        this.maxChars = maxChars;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return truncate(delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return truncate(delegate.call(toolInput, toolContext));
    }

    private String truncate(String result) {
        if (result == null || result.length() <= maxChars) {
            return result;
        }
        return result.substring(0, maxChars) + TRUNCATED_SUFFIX;
    }
}
