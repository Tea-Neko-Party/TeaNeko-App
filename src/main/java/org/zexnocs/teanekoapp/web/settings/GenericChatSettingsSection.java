package org.zexnocs.teanekoapp.web.settings;

import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.framework.config.ConfigSchema;
import org.zexnocs.teanekocore.framework.description.Description;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 已注册配置声明的纯元数据投影，不作为插件 Bean，不持有数据库或业务执行能力。
 * 插件专用适配器仍通过原注解目录发现，并优先于同一管理器的通用投影。
 *
 * <br>2026/11/04：优先显示配置声明中的简短中文名称，说明继续独立提供。
 *
 * <br>2026/11/06：将配置字段编辑器注解投影到聊天表单。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.8.4-l
 */
final class GenericChatSettingsSection implements IChatSettingsSection {
    private final ConfigManager manager;
    private final ConfigSchema schema;
    private final ObjectMapper mapper;

    /**
     * @param manager 原配置声明
     * @param mapper 原持久化转换器
     */
    GenericChatSettingsSection(ConfigManager manager, ObjectMapper mapper) {
        this.manager = manager;
        this.mapper = mapper;
        this.schema = new ConfigSchema(mapper, manager.configType());
    }

    /**
     * 2026/11/04：显示名称与完整配置说明分开维护。
     *
     * @return 声明的简短名称，旧插件没有声明时沿用原首行名称
     */
    @Override
    public String name() {
        return manager.displayName().isBlank() ? label(manager.description(), manager.value()) : manager.displayName();
    }

    /** @return 原存储、字段校验及适用命名空间声明 */
    @Override
    public ConfigManager manager() {
        return manager;
    }

    /** @return 通用字段统一使用完整 JSON 值，字符串和空值不混淆 */
    @Override
    public boolean jsonValues() {
        return true;
    }

    /**
     * @param target 已解析的真实聊天
     * @param snapshot 原事务快照
     * @return 仅包含已声明字段的表单，敏感字段不返回原值
     */
    @Override
    public CompletionStage<ChatSettings.View> view(ChatTargets.Resolved target, ConfigEdits.Snapshot snapshot) {
        try {
            var raw = mapper.readTree(snapshot.json());
            var current = mapper.valueToTree(mapper.treeToValue(raw, manager.configType()));
            var defaults = mapper.valueToTree(manager.configType().getDeclaredConstructor().newInstance());
            var fields = new ArrayList<ChatSettings.Field>();
            for (var field : schema.properties()) {
                if (field.secret()) {
                    fields.add(new ChatSettings.Field(field.name(), label(field.description(), field.name()), "受保护字段",
                            "TEXT", "此字段包含受保护内容，请使用所属模块的专用入口。", "敏感内容已隐藏", "", "专用管理", false, Map.of()));
                    continue;
                }
                var choices = choices(field);
                Class<?> type = field.type().getRawClass();
                String kind = type == boolean.class || type == Boolean.class ? "BOOLEAN"
                        : type == String.class || type == char.class || type == Character.class ? "TEXT"
                        : type.isPrimitive() || Number.class.isAssignableFrom(type) ? "NUMBER"
                        : choices.isEmpty() ? "JSON" : "SELECT";
                String source = !snapshot.registered() ? "登记后默认值"
                        : raw.has(field.jsonName()) || field.aliases().stream().anyMatch(raw::has) ? "已保存" : "类型默认值";
                fields.add(new ChatSettings.Field(field.name(), label(field.description(), field.name()), "配置项", kind,
                        field.description(), json(current.get(field.jsonName())), json(defaults.get(field.jsonName())),
                        source, field.editable(), choices, true, !type.isPrimitive()
                        && (field.nullable() || !defaults.hasNonNull(field.jsonName()) || !current.hasNonNull(field.jsonName())),
                        field.editor()));
            }
            return CompletableFuture.completedStage(new ChatSettings.View(manager.value(), name(), target.name(),
                    snapshot.registered(), snapshot.revision(), target.shared()
                    ? "此聊天配置可能与其他平台入口或机器人账号共享。" : "修改仅作用于当前聊天的这一配置。", fields, null));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("配置默认值无法创建，请检查类型的无参构造器。");
        }
    }

    /**
     * @param change 当前明确写请求
     * @throws IllegalArgumentException 当试图修改只读或敏感字段时
     */
    @Override
    public void validate(ConfigEdits.Change change) {
        for (String name : change.fields().keySet()) {
            if (!schema.require(name).editable()) {
                throw new IllegalArgumentException("当前字段不能从通用配置入口修改。");
            }
        }
    }

    /**
     * @param field 已解析字段
     * @return 枚举的实际序列化值及注解说明，非标量枚举使用 JSON 编辑器
     */
    private Map<String, String> choices(ConfigSchema.Property field) {
        var values = new LinkedHashMap<String, String>();
        if (!field.type().isEnumType()) {
            return values;
        }
        for (Object item : field.type().getRawClass().getEnumConstants()) {
            JsonNode encoded = mapper.valueToTree(item);
            if (!encoded.isString()) {
                return Map.of();
            }
            try {
                var description = field.type().getRawClass().getField(((Enum<?>) item).name()).getAnnotation(Description.class);
                values.put(encoded.stringValue(), description == null ? encoded.stringValue() : description.value());
            } catch (NoSuchFieldException failure) {
                throw new IllegalStateException("枚举字段声明无法读取。");
            }
        }
        return values;
    }

    /**
     * @param node 实际字段节点，缺失时为空
     * @return 明确的 JSON 值文本，空值不伪装为空字符串
     */
    private static String json(@org.jspecify.annotations.Nullable JsonNode node) {
        return node == null ? "null" : node.toString();
    }

    /**
     * @param description 实际说明
     * @param fallback 缺失说明时的真实字段或管理器标识
     * @return 简洁名称，完整说明保留在对应元数据中
     */
    static String label(String description, String fallback) {
        if (description.isBlank() || description.equals("尚未提供说明")) {
            return fallback;
        }
        String first = description.strip().lines().findFirst().orElseThrow();
        return first.length() > 36 ? first.substring(0, 36) + "…" : first;
    }
}
