package com.xiaohua.polaris.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ai.template.st.StTemplateRenderer;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractPromptTests {

    private final StTemplateRenderer renderer = StTemplateRenderer.builder()
            .startDelimiterToken('<')
            .endDelimiterToken('>')
            .build();

    @Test
    void rendersPlaceholdersAndKeepsJsonBraces() throws IOException {
        String template = new ClassPathResource("prompts/extract-person.st")
                .getContentAsString(StandardCharsets.UTF_8);

        String rendered = renderer.apply(template, Map.of(
                "text", "张三在杭州做工程师",
                "format", "{\"name\":\"string\"}"));

        assertThat(rendered).contains("张三在杭州做工程师");
        assertThat(rendered).contains("{\"name\":\"string\"}");
        assertThat(rendered).doesNotContain("<text>", "<format>");
    }
}
