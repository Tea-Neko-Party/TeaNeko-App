package org.zexnocs.teanekocore.file_config.parser;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.FileTypeParser;
import org.zexnocs.teanekocore.file_config.interfaces.IFileDocumentParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.InputStream;
import java.io.Serial;
import java.nio.file.Path;

/**
 * 在 YAML 节点解析后展开环境占位符，避免环境值被当作额外 YAML 结构注入。
 * 支持字符串、嵌套对象及数组值，不改写属性名；缺失必需变量时拒绝本次加载。
 * <br>2026/11/03：提供不展开环境值的原文档编辑能力，拒绝重复键。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.2.1
 * @version 4.7.0-l
 */
@FileTypeParser(FileConfigType.YAML)
public class YAMLFileTypeParser implements IFileDocumentParser {
    private final Environment environment;

    /** 使用标准系统属性及环境变量，供无 Spring 宿主直接调用。 */
    public YAMLFileTypeParser() { this(new StandardEnvironment()); }

    /**
     * 借用 Spring 当前属性来源，不在构造阶段读取或展开配置。
     * @param environment 环境变量和本地属性来源
     * @throws NullPointerException 当环境配置来源为空时
     */
    @Autowired public YAMLFileTypeParser(Environment environment) { this.environment = java.util.Objects.requireNonNull(environment, "YAML 环境配置来源不能为空。"); }

    private final ObjectMapper objectMapper = YAMLMapper.builder()
            // 2026/11/03：同一键只能有一个来源，避免编辑视图与运行绑定产生歧义。
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .findAndAddModules()
            .build();

    private final ObjectMapper localWriter = objectMapper.rebuild().annotationIntrospector(new LocalFileIntrospector()).build();

    /**
     * 2026/11/03：原文档读取不展开环境变量，也不恢复只写字段的普通导出。
     * @param bytes 原始 YAML
     * @return 原节点树
     */
    @Override
    public JsonNode readDocument(byte[] bytes) {
        return objectMapper.readTree(bytes);
    }

    /**
     * @param document 未展开的原节点树
     * @return 保留值与未知节点的 YAML，注释与排版会规范化
     */
    @Override
    public byte[] writeDocument(JsonNode document) {
        return objectMapper.writeValueAsBytes(document);
    }

    /**
     * @param type 已声明类型
     * @return 仅供本地文档能力使用的默认树
     */
    @Override
    public JsonNode defaultDocument(Class<? extends IFileConfigData> type) {
        try {
            return localWriter.valueToTree(type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("配置缺少可用默认构造器。");
        }
    }

    /**
     * 根据输入流解析成相应的 {@link IFileConfigData}
     * <br>该类不负责关闭 {@link InputStream}，请在调用类里关闭。
     *
     * @param stream 输入流
     * @param clazz  要解析的类
     * @param <T> 文件配置类型
     * @return {@link T }
     * @throws IllegalArgumentException 当配置为空，或敏感配置的格式、字段及占位符解析失败时
     */
    @Override
    public <T extends IFileConfigData> T fromFileToData(InputStream stream, Class<T> clazz) {
        try {
            var source = objectMapper.readTree(stream);
            if (source == null || source.isNull()) { throw new IllegalArgumentException("YAML 配置内容不能为空。"); }
            return objectMapper.treeToValue(resolve(source), clazz);
        } catch (RuntimeException failure) {
            var config = clazz.getAnnotation(FileConfig.class);
            if (config != null && config.sensitive()) { throw new IllegalArgumentException("敏感 YAML 配置解析失败，请检查格式、字段类型及环境变量。"); }
            throw failure;
        }
    }

    /**
     * 仅替换标量字符串值；类型转换由目标配置类型完成，Map 中的值不猜测类型。
     * @param node 已解析的 YAML 节点
     * @return 当前节点或展开后的字符串节点
     * @throws IllegalArgumentException 当必需变量缺失或占位符循环引用时
     */
    private JsonNode resolve(JsonNode node) {
        if (node.isString()) { return StringNode.valueOf(environment.resolveRequiredPlaceholders(node.stringValue())); }
        if (node instanceof ObjectNode object) {
            for (var name : java.util.List.copyOf(object.propertyNames())) { object.set(name, resolve(object.path(name))); }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) { array.set(i, resolve(array.path(i))); }
        }
        return node;
    }

    /**
     * 显式保存当前配置值，包含已展开值；敏感配置保留只写字段，普通序列化仍受限。
     * 加载不会自动写回；若要长期保留环境占位符，应编辑原文件而非保存已解析对象。
     *
     * @param path 路径
     * @param data 数据
     */
    @Override
    public void fromDataToWrite(Path path, IFileConfigData data) {
        var config = data.getClass().getAnnotation(FileConfig.class);
        var writer = config != null && config.sensitive() ? localWriter : objectMapper;
        writer.writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), data);
    }

    /**
     * 返回 YAML 配置后缀。
     * @return yml
     */
    @Override
    public String getSuffix() {
        return "yml";
    }

    /**
     * 仅供敏感配置的本地写回使用，保留只写属性及其他注解，不作用于通用导出 Mapper。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.5.0-l
     */
    private static final class LocalFileIntrospector extends JacksonAnnotationIntrospector {
        @Serial private static final long serialVersionUID = 1L;
        /**
         * 将只写字段恢复为本地文件可读写属性，其余访问策略保持原样。
         * @param config 当前映射器设置
         * @param member 被检查的字段或访问方法
         * @return 本地写回访问策略，未声明时为空
         */
        @Override public JsonProperty.@org.jspecify.annotations.Nullable Access findPropertyAccess(MapperConfig<?> config, Annotated member) {
            var access = super.findPropertyAccess(config, member);
            return access == JsonProperty.Access.WRITE_ONLY ? JsonProperty.Access.READ_WRITE : access;
        }
    }
}
