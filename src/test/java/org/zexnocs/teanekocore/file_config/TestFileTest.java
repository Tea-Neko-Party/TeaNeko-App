package org.zexnocs.teanekocore.file_config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.io.IOException;

/**
 * file 写入测试
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 */
@SpringBootTest
public class TestFileTest {
    @Autowired
    private IFileConfigService iFileConfigService;

    /**
     * 测试 write
     */
    @Test
    public void testWrite() {
        var data = iFileConfigService.get(DefaultTestFileConfig.class);
        Assertions.assertEquals("default", data.getData());
        try {
            data.setData("write");
            iFileConfigService.write(data);
            var data2 = iFileConfigService.get(DefaultTestFileConfig.class);
            Assertions.assertEquals("write", data2.getData());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除原有的文件
     *
     */
    @BeforeEach
    public void setup() {
        DefaultTestFileConfig.delete();
    }

    /**
     * 删除新建的文件
     *
     */
    @AfterEach
    public void after() {
        DefaultTestFileConfig.delete();
    }
}
