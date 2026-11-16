package com.xiaohua.polaris.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class ExtractedPersonConverterTests {

    private final BeanOutputConverter<ExtractedPerson> converter = new BeanOutputConverter<>(ExtractedPerson.class);

    @Test
    void convertsPlainJson() {
        ExtractedPerson person = converter.convert(
                "{\"name\":\"张三\",\"age\":30,\"city\":\"杭州\",\"occupation\":\"工程师\"}");

        assertThat(person).isEqualTo(new ExtractedPerson("张三", 30, "杭州", "工程师"));
    }

    @Test
    void stripsMarkdownFenceBeforeConverting() {
        ExtractedPerson person = converter.convert(
                "```json\n{\"name\":\"李四\",\"age\":null,\"city\":null,\"occupation\":null}\n```");

        assertThat(person.name()).isEqualTo("李四");
        assertThat(person.hasNoContent()).isFalse();
    }

    @Test
    void missingFieldsBecomeNull() {
        ExtractedPerson person = converter.convert("{\"name\":\"王五\"}");

        assertThat(person.age()).isNull();
        assertThat(person.city()).isNull();
        assertThat(person.occupation()).isNull();
    }

    @Test
    void serializesOnlyDeclaredFields() {
        String json = JsonMapper.builder().build()
                .writeValueAsString(new ExtractedPerson("张三", 30, "杭州", "工程师"));

        assertThat(json).isEqualTo(
                "{\"name\":\"张三\",\"age\":30,\"city\":\"杭州\",\"occupation\":\"工程师\"}");
    }

    @Test
    void nonJsonOutputFailsWithJacksonException() {
        assertThatThrownBy(() -> converter.convert("抱歉，我无法提取。"))
                .isInstanceOf(JacksonException.class);
    }
}
