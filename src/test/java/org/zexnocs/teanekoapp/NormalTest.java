package org.zexnocs.teanekoapp;

import lombok.AllArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 常规测试类，用于测试任何想要测试的功能。
 */
@SpringBootTest
public class NormalTest {
    @Autowired
    private ILogger iLogger;

    @Test
    public void run() {
        var mapper = new ObjectMapper();
        var test = """
                {
                    "type": "test",
                    "data": {
                        "key1": "value1",
                        "key2": 123,
                        "key3": true,
                        "key4": null
                    }
                }""";
        var testData = mapper.readValue(test, TestData.class);
        iLogger.info("test", "Parsed test data: {}");
    }

    @AllArgsConstructor
    public static class TestData {
        private String type;
        private Map<String, String> data;
    }
}
