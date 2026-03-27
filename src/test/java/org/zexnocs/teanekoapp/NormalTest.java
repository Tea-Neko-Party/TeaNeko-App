package org.zexnocs.teanekoapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

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
        IO.println(ChinaDateUtil.Instance.convertToDateTimeString(1771931055 * 1000L));
    }
}