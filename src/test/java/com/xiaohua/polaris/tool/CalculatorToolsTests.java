package com.xiaohua.polaris.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculatorToolsTests {

    private final CalculatorTools tools = new CalculatorTools();

    @Test
    void evaluatesArithmeticWithParentheses() {
        assertThat(tools.calculate("(12+3)*4")).isEqualTo("60");
    }

    @Test
    void evaluatesDecimals() {
        assertThat(tools.calculate("1.5 + 2.5")).isEqualTo("4.0");
    }

    @Test
    void integerDivisionStaysInteger() {
        assertThat(tools.calculate("7/2")).isEqualTo("3");
    }

    @Test
    void rejectsNonArithmeticInput() {
        assertThatThrownBy(() -> tools.calculate("System.exit(0)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.calculate("abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankAndOversizedInput() {
        assertThatThrownBy(() -> tools.calculate("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.calculate("1+".repeat(101) + "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void divisionByZeroThrows() {
        assertThatThrownBy(() -> tools.calculate("1/0"))
                .isInstanceOf(ArithmeticException.class);
    }
}
