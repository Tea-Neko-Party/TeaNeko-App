package org.zexnocs.teanekocore.file_config.parser;

import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.FileTypeParser;
import org.zexnocs.teanekocore.file_config.interfaces.IFileTypeParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 用于解析 {@link FileConfigType#YAML} 的解析器。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.1
 */
@FileTypeParser(FileConfigType.YAML)
public class YAMLFileTypeParser implements IFileTypeParser {

    private final ObjectMapper objectMapper = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .findAndAddModules()
            .build();

    /**
     * 根据输入流解析成相应的 {@link IFileConfigData}
     * <br>该类不负责关闭 {@link InputStream}，请在调用类里关闭。
     *
     * @param stream 输入流
     * @param clazz  要解析的类
     * @return {@link T }
     */
    @Override
    public <T extends IFileConfigData> T fromFileToData(InputStream stream, Class<T> clazz) {
        return objectMapper.readValue(stream, clazz);
    }

    /**
     * 根据 IFileConfigData 来写入到文件中。
     *
     * @param path 路径
     * @param data 数据
     */
    @Override
    public void fromDataToWrite(Path path, IFileConfigData data) {
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), data);
    }

    @Override
    public String getSuffix() {
        return "yml";
    }
}