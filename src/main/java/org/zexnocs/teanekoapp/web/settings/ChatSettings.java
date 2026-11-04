package org.zexnocs.teanekoapp.web.settings;

import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import java.util.List;
import java.util.Map;

/**
 * 面向人的聊天设置 DTO，不导出数据库对象、内部 scope 或认证上下文。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：通用字段声明 JSON 值协议及配置分组，保留既有专用字段构造入口。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
public final class ChatSettings {
    /** 禁止实例化视图命名空间。 */
    private ChatSettings() {}

    /**
     * 分组字段的当前值、类型默认值及可编辑合同。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：区分完整 JSON 值与旧文本协议，声明允许空值的控件。
     *
     * @param name 稳定字段标识
     * @param label 中文名称
     * @param group 中文分组
     * @param kind BOOLEAN、NUMBER、TEXT、LONG_TEXT、SELECT 或 JSON
     * @param description 中文用途及约束
     * @param value 当前预览或实际值
     * @param defaultValue 类型默认值
     * @param source 当前值来源
     * @param editable 是否允许本入口修改
     * @param choices 有限选项值与中文标签
     * @param jsonValue 是否为完整 JSON 值协议
     * @param nullable 是否提供显式空值选项，保存仍执行原字段校验
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.1-l
     */
    public record Field(String name, String label, String group, String kind, String description,
                        String value, String defaultValue, String source, boolean editable,
                        Map<String, String> choices, boolean jsonValue, boolean nullable) {
        /**
         * 2026/11/04：保留专用适配器的既有文本字段协议。
         * @param name 字段标识
         * @param label 中文名称
         * @param group 显示分组
         * @param kind 控件类型
         * @param description 说明
         * @param value 当前值
         * @param defaultValue 默认值
         * @param source 来源
         * @param editable 可编辑性
         * @param choices 选项
         */
        public Field(String name, String label, String group, String kind, String description,
                String value, String defaultValue, String source, boolean editable, Map<String, String> choices) {
            this(name, label, group, kind, description, value, defaultValue, source, editable, choices, false, false);
        }
    }

    /**
     * 经过本地授权的配置目录项，区分 Agent 与其他业务配置。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：提供稳定分组与完整用途说明，发现不代替授权。
     *
     * @param id 稳定区标识
     * @param name 中文名称
     * @param group 稳定分组
     * @param description 配置完整用途说明
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.1-l
     */
    public record Section(String id, String name, String group, String description) {
        /**
         * 2026/11/04：保留旧模块的目录构造，分组须由新的目录元数据明确声明。
         * @param id 稳定标识
         * @param name 显示名称
         */
        public Section(String id, String name) {
            this(id, name, "general", name);
        }
    }

    /**
     * 实际当前读形成的编辑页。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param section 区标识
     * @param name 中文区名
     * @param targetName 真实聊天名称
     * @param registered 是否已登记
     * @param revision 原子版本
     * @param sharedExplanation 真实共享影响
     * @param fields 已声明字段
     * @param receipt 最近一次本操作回执，可为空
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record View(String section, String name, String targetName, boolean registered, String revision,
                       String sharedExplanation, List<Field> fields,
                       ConfigEdits.@org.jspecify.annotations.Nullable Receipt receipt) {}
}
