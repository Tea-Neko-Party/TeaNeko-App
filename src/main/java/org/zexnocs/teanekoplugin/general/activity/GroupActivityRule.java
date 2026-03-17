package org.zexnocs.teanekoplugin.general.activity;

import lombok.Getter;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 一个 Group 活跃度规则。
 * <br>返回 true 表示用户活跃度不足。
 *
 * @author zExNocs
 * @date 2026/03/17
 * @since 4.3.4
 */
public class GroupActivityRule {
    /// 静态的解析器
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /// 表达式字符串
    @Getter
    private final String expressionString;

    /// 缓存解析出的语法树对象
    private final Expression expression;

    /**
     * 使用表达式创建一个 rule
     *
     * @param expressionStr 表达式
     * @throws SpelParseException 如果解析失败
     */
    public GroupActivityRule(String expressionStr) throws IllegalArgumentException {
        this.expressionString = expressionStr;
        try {
            this.expression = PARSER.parseExpression(expressionStr);
        } catch (SpelParseException e) {
            throw new IllegalArgumentException("表达式解析失败: " + expressionStr, e);
        }
    }

    /**
     * 检测用户是否符合该表达式
     *
     * @param data 用户活跃度数据
     * @return boolean 如果符合表达式，则返回 true；一般来说返回 true 表示用户活跃度不足。
     */
    public boolean isValid(GroupActivityData data) throws IllegalArgumentException {
        Boolean value;
        try {
            value = expression.getValue(data, Boolean.class);
        } catch (SpelEvaluationException e) {
            throw new IllegalArgumentException("表达式计算返回不是 boolean 或使用了不存在的字段", e);
        }
        if(value == null) {
            throw new IllegalArgumentException("表达式计算结果为 null");
        }
        return value;
    }
}