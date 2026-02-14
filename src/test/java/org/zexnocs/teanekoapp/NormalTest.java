package org.zexnocs.teanekoapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.support.CronExpression;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.time.ZonedDateTime;

/**
 * 常规测试类，用于测试任何想要测试的功能。
 */
@SpringBootTest
public class NormalTest {
    @Test
    void run() {
        String expression = "0 0 18 * * ?";
        var cron = CronExpression.parse(expression);
        System.out.println(ChinaDateUtil.Instance.convertToDateTimeString(
                ChinaDateUtil.Instance.getNextTriggerTime(cron, ZonedDateTime.now())));
    }
}
