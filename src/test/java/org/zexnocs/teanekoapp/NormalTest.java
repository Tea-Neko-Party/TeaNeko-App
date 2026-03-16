package org.zexnocs.teanekoapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.time.temporal.ChronoUnit;

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
        var prevLast = ChinaDateUtil.Instance.convertToChinaDate(1743354000000L);
        var currLast = ChinaDateUtil.Instance.convertToChinaDate(1746550800000L);
        var currFirst = currLast.minusDays(0);
        long gapDays = ChronoUnit.DAYS.between(prevLast, currFirst) - 1;
        var a = prevLast.plusDays(1);
        var b = currFirst.minusDays(1);
        System.out.printf("""
                prevLast: %s
                currLast: %s
                currFirst: %s
                gapDays: %s
                a: %s
                b: %s
                %n""", prevLast, currLast, currFirst, gapDays, a, b);
    }
}