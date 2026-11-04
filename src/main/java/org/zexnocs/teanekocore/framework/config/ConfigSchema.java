package org.zexnocs.teanekocore.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.zexnocs.teanekocore.framework.description.Description;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyName;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * 依据实际序列化契约解析配置字段，统一继承、泛型、别名及敏感内容识别。
 * 元数据不持有配置实例，数据库配置与本地文件投影共用该契约。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
public final class ConfigSchema {
    private final List<Property> properties;

    /**
     * @param mapper 与持久化或文件解析一致的转换器
     * @param type 配置实际类型
     */
    public ConfigSchema(ObjectMapper mapper, Class<?> type) {
        var beanType = mapper.constructType(type);
        var config = mapper.deserializationConfig();
        var introspector = config.classIntrospectorInstance().forOperation(config);
        var bean = introspector.introspectForDeserialization(beanType, introspector.introspectClassAnnotations(beanType));
        properties = bean.findProperties().stream().filter(value -> value.getField() != null)
                .map(value -> {
                    var field = value.getField();
                    var description = field.getAnnotation(Description.class);
                    var declared = value.getPrimaryMember().getAnnotation(JsonProperty.class);
                    boolean secret = declared != null && declared.access() == JsonProperty.Access.WRITE_ONLY
                            || sensitive(mapper, value.getPrimaryType(), new HashSet<>());
                    boolean editable = value.couldDeserialize() && !Modifier.isFinal(field.getModifiers())
                            && !Modifier.isStatic(field.getModifiers()) && !secret;
                    return new Property(field.getName(), value.getName(), value.getPrimaryType(),
                            description == null ? "尚未提供说明" : description.value(), editable, secret,
                            value.findAliases().stream().map(PropertyName::getSimpleName).toList(),
                            field.getAnnotated().getAnnotatedType().isAnnotationPresent(org.jspecify.annotations.Nullable.class));
                }).toList();
        if (properties.stream().map(Property::name).distinct().count() != properties.size()) {
            throw new IllegalArgumentException("配置存在重复字段映射。");
        }
    }

    /** @return 按类型声明顺序排列的字段契约 */
    public List<Property> properties() {
        return properties;
    }

    /**
     * @param name Java 字段标识，不接受任意 JSON 指针
     * @return 固定字段元数据
     * @throws NoSuchElementException 当字段未声明时
     */
    public Property require(String name) {
        return properties.stream().filter(value -> value.name().equals(name)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("配置字段不存在。"));
    }

    /**
     * 防止显式 JSON 值通过标量强制转换变成另一种设置。
     * @param property 实际字段类型
     * @param value 完整 JSON 候选
     * @throws IllegalArgumentException 当标量形状与字段合同不一致时
     */
    public static void validateValue(Property property, JsonNode value) {
        Class<?> raw = property.type().getRawClass();
        if (value.isMissingNode() || value.isNull() && raw.isPrimitive()) {
            throw new IllegalArgumentException("配置字段缺少有效值，基本类型不能设置为空。");
        }
        if (value.isNull()) {
            return;
        }
        boolean numeric = Number.class.isAssignableFrom(raw)
                || raw.isPrimitive() && raw != boolean.class && raw != char.class;
        if ((raw == String.class || raw == Character.class || raw == char.class) && !value.isString()
                || (raw == boolean.class || raw == Boolean.class) && !value.isBoolean()
                || numeric && !value.isNumber()) {
            throw new IllegalArgumentException("JSON 值类型与配置字段不一致。");
        }
        if (numeric && !Set.of(float.class, Float.class, double.class, Double.class,
                java.math.BigDecimal.class, Number.class).contains(raw) && !value.isIntegralNumber()) {
            throw new IllegalArgumentException("该配置字段必须使用整数。");
        }
    }

    /**
     * 递归检查明确的只写或忽略类型，不因循环引用无限展开。
     * @param mapper 原转换器
     * @param type 已解析泛型的字段类型
     * @param visited 本次遍历路径
     * @return 是否包含不能由普通表单公开的内容
     */
    private static boolean sensitive(ObjectMapper mapper, JavaType type, Set<JavaType> visited) {
        if (!visited.add(type)) {
            return false;
        }
        var ignored = type.getRawClass().getAnnotation(JsonIgnoreType.class);
        if (ignored != null && ignored.value()) {
            return true;
        }
        if (type.isContainerType()) {
            return type.getContentType() != null && sensitive(mapper, type.getContentType(), visited);
        }
        if (type.isPrimitive() || type.isEnumType() || type.getRawClass().getName().startsWith("java.")) {
            return false;
        }
        var config = mapper.deserializationConfig();
        var introspector = config.classIntrospectorInstance().forOperation(config);
        return introspector.introspectForDeserialization(type, introspector.introspectClassAnnotations(type))
                .findProperties().stream().anyMatch(value -> {
                    var member = value.getPrimaryMember();
                    var annotation = member == null ? null : member.getAnnotation(JsonProperty.class);
                    return annotation != null && annotation.access() == JsonProperty.Access.WRITE_ONLY
                            || sensitive(mapper, value.getPrimaryType(), visited);
                });
    }

    /**
     * 只供受信配置服务使用的元数据，不作为普通响应序列化。
     *
     * @param name Java 字段标识
     * @param jsonName 实际序列化键
     * @param type 已解析父类泛型的字段类型
     * @param description 字段说明
     * @param editable 是否可由通用入口修改
     * @param secret 是否含敏感内容
     * @param aliases 反序列化兼容别名
     * @param nullable 是否显式声明允许空值，默认空值另由配置类型确认
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    @JsonIgnoreType
    public record Property(String name, String jsonName, JavaType type, String description,
                           boolean editable, boolean secret, List<String> aliases, boolean nullable) {}
}
