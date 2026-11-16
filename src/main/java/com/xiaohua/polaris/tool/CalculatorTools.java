package com.xiaohua.polaris.tool;

import java.util.regex.Pattern;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.StringUtils;

public class CalculatorTools {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final Pattern ALLOWED = Pattern.compile("[0-9+\\-*/().\\s]+");
    private static final int MAX_EXPRESSION_LENGTH = 200;

    @Tool(name = "calculate", description = "计算算术表达式，支持 + - * / 和括号；整数除法按整数计算。需要精确计算时使用，不要心算。")
    public String calculate(
            @ToolParam(description = "算术表达式，例如 (12+3)*4 或 1.5+2.5", required = true)
            String expression) {
        if (!StringUtils.hasText(expression)) {
            throw new IllegalArgumentException("expression 不能为空");
        }
        String trimmed = expression.trim();
        if (trimmed.length() > MAX_EXPRESSION_LENGTH) {
            throw new IllegalArgumentException("expression 长度不能超过 " + MAX_EXPRESSION_LENGTH);
        }
        if (!ALLOWED.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("expression 只允许数字、括号、小数点、空格和 + - * /");
        }
        Object value = PARSER.parseExpression(trimmed)
                .getValue(SimpleEvaluationContext.forReadOnlyDataBinding().build());
        if (value == null) {
            throw new IllegalArgumentException("expression 没有计算结果");
        }
        return String.valueOf(value);
    }
}
