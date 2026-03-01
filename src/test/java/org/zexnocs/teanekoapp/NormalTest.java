package org.zexnocs.teanekoapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
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
        var rawData = """
                {
                    "key1": "value1",
                    "key2": 123,
                    "key3": [1, 2, 3],
                    "key4": {
                        "subKey1": "subValue1",
                        "subKey2": 456
                    }
                }
                """;

        var objectMapper = new ObjectMapper();
        var collectionType = objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class);
        var parsedData = objectMapper.readValue(rawData, collectionType);
        iLogger.info("test", "Parsed Data: " + parsedData);
    }
}
