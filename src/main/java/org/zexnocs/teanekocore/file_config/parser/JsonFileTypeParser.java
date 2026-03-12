package org.zexnocs.teanekocore.file_config.parser;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.FileTypeParser;
import org.zexnocs.teanekocore.file_config.interfaces.IFileTypeParser;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 用于解析 {@link FileConfigType#JSON} 的解析器。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 */
@RequiredArgsConstructor
@FileTypeParser(FileConfigType.JSON)
public class JsonFileTypeParser implements IFileTypeParser {

    private final ObjectMapper customObjectMapper;

    /**
     * 根据输入流解析成相应的 {@link IFileConfigData}
     * <br>该类不负责关闭 {@link InputStream}，请在调用类里关闭。
     *
     * @param stream 输入流
     * @param clazz  要解析的类
     * @return {@link T }
     */
    @Override
    public <T extends IFileConfigData> T fromFile(InputStream stream, Class<T> clazz) {
        return customObjectMapper.readValue(stream, clazz);
    }

    /**
     * 根据 IFileConfigData 来写入到文件中。
     *
     * @param path 路径
     * @param data 数据
     */
    @Override
    public void fromData(Path path, IFileConfigData data) {
        customObjectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), data);
    }
}
