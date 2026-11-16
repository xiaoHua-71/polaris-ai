package com.xiaohua.polaris.tool;

import java.time.DateTimeException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeToolsTests {

    private final TimeTools tools = new TimeTools();

    @Test
    void blankZoneDefaultsToShanghai() {
        assertThat(tools.getCurrentTime(null)).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\+08:00");
        assertThat(tools.getCurrentTime("  ")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\+08:00");
    }

    @Test
    void usesRequestedZone() {
        assertThat(tools.getCurrentTime("UTC")).endsWith("Z");
    }

    @Test
    void invalidZoneThrows() {
        assertThatThrownBy(() -> tools.getCurrentTime("Not/AZone"))
                .isInstanceOf(DateTimeException.class);
    }
}
