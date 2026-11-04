package org.zexnocs.teanekocore.file_config.parser;

import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.FileTypeParser;
import org.zexnocs.teanekocore.file_config.interfaces.IFileDocumentParser;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 用于解析 {@link FileConfigType#JSON} 的解析器。
 * <br>2026/11/03：支持原始文档局部编辑，并拒绝重复键。
 * <br>2026/11/04：通用表单复用本 JSON 解析器的命名与类型元数据。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 * @version 4.7.1-l
 */
@FileTypeParser(FileConfigType.JSON)
public class JsonFileTypeParser implements IFileDocumentParser {
    /**
     * @param type 原注册配置类型
     * @return 与本 JSON 映射一致的字段定义
     */
    @Override
    public org.zexnocs.teanekocore.framework.config.ConfigSchema schema(Class<? extends IFileConfigData> type) {
        return new org.zexnocs.teanekocore.framework.config.ConfigSchema(objectMapper, type);
    }

    /// object mapper，支持注释。
    private final ObjectMapper objectMapper = JsonMapper.builder()
            // 2026/11/03：文件编辑和原加载共享无重复键的节点合同。
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
            .findAndAddModules()
            .build();

    /**
     * 2026/11/03：提供同一解析器的原文档能力。
     * @param bytes 原始 JSON
     * @return 原节点树
     */
    @Override
    public tools.jackson.databind.JsonNode readDocument(byte[] bytes) {
        return objectMapper.readTree(bytes);
    }

    /**
     * @param document 原节点树
     * @return 规范化 JSON
     */
    @Override
    public byte[] writeDocument(tools.jackson.databind.JsonNode document) {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
    }

    /**
     * @param type 已声明类型
     * @return 默认原文档
     */
    @Override
    public tools.jackson.databind.JsonNode defaultDocument(Class<? extends IFileConfigData> type) {
        try {
            return objectMapper.valueToTree(type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("配置缺少可用默认构造器。");
        }
    }

    /**
     * 根据输入流解析成相应的 {@link IFileConfigData}
     * <br>该类不负责关闭 {@link InputStream}，请在调用类里关闭。
     *
     * @param stream 输入流
     * @param <T> 文件配置类型
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

    /**
     * 获取该数据的后缀名，不包括 "."。
     * <br>例如 JSON 则返回 "json"
     *
     * @return 后缀名
     */
    @Override
    public String getSuffix() {
        return "json";
    }
}
