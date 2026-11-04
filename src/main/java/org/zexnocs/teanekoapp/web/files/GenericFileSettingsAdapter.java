package org.zexnocs.teanekoapp.web.files;

import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.framework.config.ConfigSchema;

import java.util.List;

/**
 * 无秘密本地文件的自动表单元数据；未知资源生命周期明确要求重启，不伪造热更新成功。
 * 此对象没有 Spring 业务依赖，专用 Bean 适配器继续通过原注解注册。
 *
 * @param type 原固定类型
 * @param name 显示名称
 * @param fields 与实际文件解析器一致的字段
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
record GenericFileSettingsAdapter(Class<? extends IFileConfigData> type, String name,
                                  List<FileSettings.Rule> fields) implements IFileSettingsAdapter {
    /**
     * @param schema 原文件解析器的字段契约
     * @return 统一的字段投影，说明仅由类型注解维护
     */
    static List<FileSettings.Rule> rules(ConfigSchema schema) {
        return schema.properties().stream().filter(ConfigSchema.Property::editable).map(value -> {
            Class<?> raw = value.type().getRawClass();
            String kind = raw == boolean.class || raw == Boolean.class ? "BOOLEAN"
                    : raw == String.class ? "TEXT" : raw.isPrimitive() || Number.class.isAssignableFrom(raw) ? "NUMBER" : "JSON";
            return new FileSettings.Rule("/" + value.jsonName().replace("~", "~0").replace("/", "~1"),
                    value.description().equals("尚未提供说明") ? value.name() : value.description().lines().findFirst().orElseThrow(),
                    value.description(), kind, false);
        }).toList();
    }

    /** @return 未声明热更新合同的配置需要重启 */
    @Override
    public String application() {
        return "RESTART";
    }

    /**
     * @param candidate 经原文件解析器完成类型绑定的候选
     */
    @Override
    public void validate(IFileConfigData candidate) {
        type.cast(candidate);
    }

    /** @return 明确说明通用预检的实际边界 */
    @Override
    public String validationNote() {
        return "格式、环境变量及字段类型检查通过；此配置需重启，由原模块确认运行设置。尚未保存或应用。";
    }

    /**
     * @param published 原候选
     * @return 不会返回，此适配器只允许重启方式
     * @throws UnsupportedOperationException 当调用方绕过重启合同试图确认热更新时
     */
    @Override
    public String verifyApplied(IFileConfigData published) {
        throw new UnsupportedOperationException("该配置没有声明热更新合同。");
    }
}
