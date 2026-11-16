package com.xiaohua.polaris.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractedPersonTests {

    @Test
    void allFieldsMissingIsEmpty() {
        assertThat(new ExtractedPerson(null, null, " ", null).hasNoContent()).isTrue();
        assertThat(new ExtractedPerson("", null, null, null).hasNoContent()).isTrue();
    }

    @Test
    void anyFieldPresentIsNotEmpty() {
        assertThat(new ExtractedPerson("张三", null, null, null).hasNoContent()).isFalse();
        assertThat(new ExtractedPerson(null, 30, null, null).hasNoContent()).isFalse();
        assertThat(new ExtractedPerson(null, null, "杭州", null).hasNoContent()).isFalse();
        assertThat(new ExtractedPerson(null, null, null, "工程师").hasNoContent()).isFalse();
    }
}
