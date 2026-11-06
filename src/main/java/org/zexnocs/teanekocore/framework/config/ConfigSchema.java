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
 * <br>2026/11/04：按真实 JSON 指针解析嵌套和泛型属性说明。
 * <br>2026/11/06：通过字段或值类型注解提供编辑器元数据。
 *
 * <br>2026/11/06：合并字段与值类型编辑器声明，修正集合根字段定位。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.8.4-l
 */
public final class ConfigSchema {
    private final List<Property> properties;
    // 2026/11/04：复用实际解析器的类型和命名规则，为显式文件字段定位说明。
    private final ObjectMapper mapper;

    /**
     * @param mapper 与持久化或文件解析一致的转换器
     * @param type 配置实际类型
     */
    public ConfigSchema(ObjectMapper mapper, Class<?> type) {
        this(mapper, mapper.constructType(type));
    }

    /**
     * @param mapper 原格式解析器
     * @param beanType 保留泛型绑定的当前类型
     */
    private ConfigSchema(ObjectMapper mapper, JavaType beanType) {
        this.mapper = mapper;
        var config = mapper.deserializationConfig();
        var introspector = config.classIntrospectorInstance().forOperation(config);
        var bean = introspector.introspectForDeserialization(beanType, introspector.introspectClassAnnotations(beanType));
        properties = bean.findProperties().stream().filter(value -> value.getField() != null)
                .map(value -> {
                    var field = value.getField();
                    var description = field.getAnnotation(Description.class);
                    var editor = field.getAnnotation(ValueEditor.class);
                    if (editor == null) {
                        editor = value.getPrimaryType().getRawClass().getAnnotation(ValueEditor.class);
                    }
                    var declared = value.getPrimaryMember().getAnnotation(JsonProperty.class);
                    boolean secret = declared != null && declared.access() == JsonProperty.Access.WRITE_ONLY
                            || sensitive(mapper, value.getPrimaryType(), new HashSet<>());
                    boolean editable = value.couldDeserialize() && !Modifier.isFinal(field.getModifiers())
                            && !Modifier.isStatic(field.getModifiers()) && !secret;
                    return new Property(field.getName(), value.getName(), value.getPrimaryType(),
                            description == null ? "尚未提供说明" : description.value(), editable, secret,
                            value.findAliases().stream().map(PropertyName::getSimpleName).toList(),
                            field.getAnnotated().getAnnotatedType().isAnnotationPresent(org.jspecify.annotations.Nullable.class),
                            editor == null ? "AUTO" : editor.value());
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
     * 2026/11/04：只解析声明的属性路径，支持嵌套、数组与泛型，不读取配置实例。
     * @param pointer 已开放字段的实际 JSON 指针
     * @return 对应属性说明；无法映射到声明字段时为空
     */
    public java.util.Optional<Property> findPointer(String pointer) {
        var schema = this;
        Property property = null;
        String[] segments = pointer.substring(1).split("/");
        for (int index = 0; index < segments.length; index++) {
            String name = segments[index].replace("~1", "/").replace("~0", "~");
            property = schema.properties.stream().filter(value -> value.jsonName().equals(name)
                    || value.aliases().contains(name)).findFirst().orElse(null);
            if (property == null) {
                return java.util.Optional.empty();
            }
            JavaType next = property.type();
            if ((next.isCollectionLikeType() || next.isArrayType()) && index < segments.length - 1) {
                if (++index >= segments.length || !segments[index].matches("[0-9]+")) {
                    return java.util.Optional.empty();
                }
                next = next.getContentType();
            }
            if (index < segments.length - 1) {
                schema = new ConfigSchema(mapper, next);
            }
        }
        return java.util.Optional.ofNullable(property);
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
     * <br>2026/11/06：投影值编辑器声明，保留原字段与构造合同。
     *
     * @param name Java 字段标识
     * @param jsonName 实际序列化键
     * @param type 已解析父类泛型的字段类型
     * @param description 字段说明
     * @param editable 是否可由通用入口修改
     * @param secret 是否含敏感内容
     * @param aliases 反序列化兼容别名
     * @param nullable 是否显式声明允许空值，默认空值另由配置类型确认
     * @param editor 编辑器标识，AUTO 表示使用原字段种类
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.8.4-l
     */
    @JsonIgnoreType
    public record Property(String name, String jsonName, JavaType type, String description,
                           boolean editable, boolean secret, List<String> aliases, boolean nullable, String editor) {}
}
