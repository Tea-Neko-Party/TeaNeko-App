package org.zexnocs.teanekoapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * 常规测试类，用于测试任何想要测试的功能。
 * <br>建议测试完删除。
 *
 * @author zExNocs
 * @date 2026/02/10
 * @since 4.0.0
 */
@SpringBootTest
public class NormalTest {
    @Test
    public void run() {
        SpelExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("a", true);
        context.setVariable("b", false);
        Expression expression = parser.parseExpression("!#a");
        System.out.println(expression.getValue(context));
    }
}