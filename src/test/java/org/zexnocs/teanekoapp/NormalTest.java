package org.zexnocs.teanekoapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

/**
 * 常规测试类，用于测试任何想要测试的功能。
 */
@SpringBootTest
public class NormalTest {
    @Autowired
    private ILogger iLogger;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void run() {
        GeneralEasyData.of("test").get("test").getTaskConfig("test")
                .set("test", "*".repeat(1))
                .pushWithFuture()
                .finish()
                .join();
    }
}
