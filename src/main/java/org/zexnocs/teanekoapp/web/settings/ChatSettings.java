package org.zexnocs.teanekoapp.web.settings;

import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import java.util.List;
import java.util.Map;

/**
 * 面向人的聊天设置 DTO，不导出数据库对象、内部 scope 或认证上下文。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public final class ChatSettings {
    /** 禁止实例化视图命名空间。 */
    private ChatSettings() {}

    /**
     * 分组字段的当前值、类型默认值及可编辑合同。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param name 稳定字段标识
     * @param label 中文名称
     * @param group 中文分组
     * @param kind BOOLEAN、NUMBER、TEXT、LONG_TEXT 或 SELECT
     * @param description 中文用途及约束
     * @param value 当前预览或实际值
     * @param defaultValue 类型默认值
     * @param source 当前值来源
     * @param editable 是否允许本入口修改
     * @param choices 有限选项值与中文标签
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Field(String name, String label, String group, String kind, String description,
                        String value, String defaultValue, String source, boolean editable,
                        Map<String, String> choices) {}

    /**
     * 一个声明式业务配置区，不代表反射开放所有 ConfigManager。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 稳定区标识
     * @param name 中文名称
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Section(String id, String name) {}

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
