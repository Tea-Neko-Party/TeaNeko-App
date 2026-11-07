package org.zexnocs.teanekocore.file_config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekoapp.TeaNekoAppApplication;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.io.IOException;

/**
 * file 写入测试
 * <br>2026/11/04：删除测试文件后重新加载缓存，避免旧缓存触发磁盘版本并发保护。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 * @version 4.7.2-l
 */
@SpringBootTest(classes = TeaNekoAppApplication.class)
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
     * 删除原有测试文件，并同步刷新该测试宿主的缓存状态。
     * 2026/11/04：写入前重新加载，保留生产代码对旧缓存覆盖磁盘的拒绝。
     *
     */
    @BeforeEach
    public void setup() {
        DefaultTestFileConfig.delete();
        iFileConfigService.reload();
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
