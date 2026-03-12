package org.zexnocs.teanekocore.file_config;

import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.io.IOException;
import java.nio.file.Files;

/**
 * 测试用的 file config
 * <br>应该存放在 "config/test" 中
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 */
@FileConfig(
        value = "default_config",
        path = "test"
)
@Getter
@Setter
public class DefaultTestFileConfig implements IFileConfigData {
    /**
     * 数据值
     */
    private String data;

    public DefaultTestFileConfig() {
        this.data = "default";
    }

    /**
     * 删除这个 config
     *
     */
    public static void delete() {
        var file = FileConfigService.ROOT_PATH.resolve("test")
                .resolve("default_config.json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
