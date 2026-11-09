package org.zexnocs.teanekoagent.integration.web;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.configuration.file_config.AgentMainFileConfig;
import org.zexnocs.teanekoapp.web.files.FileSettings;
import org.zexnocs.teanekoapp.web.files.IFileSettingsAdapter;
import org.zexnocs.teanekoapp.web.files.TeaNekoFileSettings;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.util.List;

/**
 * Agent 各文件按模块贡献显式表单及原语义校验，不反射开放任意字段。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：模型表单提供上下文窗口与输入比例配置。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/04：模型文件编辑声明增加协议字段，允许唯一配置路由复用协议。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
 * <br>2026/11/05：模型连接地址及路径按普通字段显示，密钥继续隐藏。
 * <br>2026/11/06：新增观测日志专用热应用表单。
 * <br>2026/11/06：观测表单补充上下文、推理和汇总长度配置。
 * <br>2026/11/06：按独立开关和日志级别输出精简文字汇总，去重并继续脱敏。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 * <br>2026/11/07：开放自动表情解析概率的模式及两套参数说明。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
public final class AgentFileSettings {
    /** 禁止实例化贡献命名空间。 */
    private AgentFileSettings() {}

    /**
     * @param path 原文件字段
     * @param label 中文名
     * @param kind 明确编辑类型
     * @return 非敏感字段规则
     */
    public static FileSettings.Rule field(String path, String label, String kind) {
        return new FileSettings.Rule(path, label, "保留原环境模板；设置后使用原模块范围校验。", kind, false);
    }

    /**
     * @param path 受保护原字段
     * @param label 中文名
     * @param kind 编辑类型
     * @return 只接受显式替换或清除的规则
     */
    public static FileSettings.Rule secret(String path, String label, String kind) {
        return new FileSettings.Rule(path, label, "不返回原值或展开值；留在保留状态不会改写，替换可填写环境占位符。", kind, true);
    }

    /**
     * 复用文件身份确认，具体模块补充实际消费端口检查。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public abstract static class Base implements IFileSettingsAdapter {
        protected final IFileConfigService files;
        private final Class<? extends IFileConfigData> type;
        private final String name;
        private final List<FileSettings.Rule> fields;

        /**
         * @param files 原加载器
         * @param type 固定文件类型
         * @param name 中文名
         * @param fields 明确字段
         */
        protected Base(IFileConfigService files, Class<? extends IFileConfigData> type, String name, List<FileSettings.Rule> fields) {
            this.files = files;
            this.type = type;
            this.name = name;
            this.fields = List.copyOf(fields);
        }
        /** @return 固定中文名 */
        @Override
        public String name() {
            return name;
        }
        /** @return 固定文件类型 */
        @Override
        public Class<? extends IFileConfigData> type() {
            return type;
        }
        /** @return 固定字段规则 */
        @Override
        public List<FileSettings.Rule> fields() {
            return fields;
        }
        /** @return 已知消费端在后续操作读取新快照 */
        @Override
        public String application() {
            return "NEXT_USE";
        }
        /**
         * @param published 原发布候选
         * @return 确认同一运行缓存身份，具体模块可收紧确认
         */
        @Override
        public String verifyApplied(IFileConfigData published) {
            if (files.requireCurrent(type) != published) {
                throw new IllegalStateException("模块文件缓存已被其他发布替换。");
            }
            return "模块新快照已确认，后续按需读取生效；在途操作保留原快照。";
        }
    }

/**
     * Agent 安全观测开关使用明确的按需热应用合同。
     * <br>2026/11/06：新快照从下一个观察节点起生效，不重放已有日志。
     * <br>2026/11/06：增加汇总内容选择及有界字数设置。
     * <br>2026/11/06：按独立开关和日志级别输出精简文字汇总，去重并继续脱敏。
     *
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.9.0-l
     */
    @Component
    // 2026/11/07：原生系统能力不作为业务插件启停。
    @TeaNekoFileSettings("agent.observability")
    public static final class Observability extends Base {
        /**
         * 2026/11/08：分别展示摘要、DEBUG 预览及独立完整诊断，不把预览开关标作所有日志的开关。
         * @param files 原文件配置入口
         */
        public Observability(IFileConfigService files) {
            super(files, org.zexnocs.teanekoagent.configuration.file_config.AgentObservationFileConfig.class,
                    "Agent 观测日志", List.of(field("/llm-info", "独立 LLM 汇总", "BOOLEAN"),
                            field("/agent-info", "Agent 请求汇总", "BOOLEAN"),
                            field("/visibility", "摘要级别（INFO / DEBUG / NONE）", "TEXT"),
                            field("/include-context", "DEBUG 正文预览总开关", "BOOLEAN"),
                            field("/include-user", "应用输入预览", "BOOLEAN"),
                            field("/include-final-output", "最终回复预览", "BOOLEAN"),
                            field("/include-system", "系统输入预览", "BOOLEAN"),
                            field("/include-schema", "结构与工具定义预览", "BOOLEAN"),
                            field("/include-tools", "工具调用链预览", "BOOLEAN"),
                            field("/include-reasoning", "供应商推理预览", "BOOLEAN"),
                            field("/max-content-characters", "DEBUG 预览字符预算（实际最大2048）", "NUMBER"),
                            field("/diagnostic-log", "独立完整诊断日志", "BOOLEAN")));
        }

        /** @param candidate 已解析候选 */
        @Override
        public void validate(IFileConfigData candidate) {
            if (!(candidate instanceof org.zexnocs.teanekoagent.configuration.file_config.AgentObservationFileConfig settings)) {
                throw new IllegalArgumentException("Agent 观测配置类型不匹配。");
            }
            // 2026/11/06：防止日志内容配置造成无界内存占用。
            if (settings.getVisibility() == null) {
                throw new IllegalArgumentException("日志级别必须为 INFO、DEBUG 或 NONE。");
            }
            // 2026/11/08：旧上限配置仍可载入，预览入口另行限制最大2048字符。
            if (settings.getMaxContentCharacters() < 128 || settings.getMaxContentCharacters() > 2_000_000) {
                throw new IllegalArgumentException("DEBUG 预览字符预算必须为 128–2000000，实际输出上限为2048。");
            }
        }
    }

    /**
     * Agent 总开关表单，不借配置应用直接触发模型。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    @Component
    // 2026/11/07：原生系统能力不作为业务插件启停。
    @TeaNekoFileSettings("agent.main")
    public static final class Main extends Base {
        /** @param files 原文件入口 */
        public Main(IFileConfigService files) {
            super(files, AgentMainFileConfig.class, "Agent 总开关", List.of(field("/enabled", "启用 Agent", "BOOLEAN")));
        }
        /**
         * 2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
         * @param candidate 原类型候选
         */
        @Override
        public void validate(IFileConfigData candidate) {
            if (!(candidate instanceof AgentMainFileConfig)) {
                throw new IllegalArgumentException("Agent 总开关候选类型不匹配。");
            }
        }
        /**
         * @param published 原候选
         * @return 下一次准入检查的开关确认
         */
        @Override
        public String verifyApplied(IFileConfigData published) {
            super.verifyApplied(published);
            return "总开关已发布，后续准入检查读取新值；模型就绪状态仍独立校验。";
        }
    }


    // 2026/11/07：系统模型表单由独立 LLM 子模块提供。
}
