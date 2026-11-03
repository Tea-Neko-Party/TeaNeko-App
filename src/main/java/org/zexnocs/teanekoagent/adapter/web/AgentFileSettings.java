package org.zexnocs.teanekoagent.adapter.web;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.adapter.personality.ConfiguredPersonalities;
import org.zexnocs.teanekoagent.expression.ExpressionRuntime;
import org.zexnocs.teanekoagent.file_config.AgentExpressionFileConfig;
import org.zexnocs.teanekoagent.file_config.AgentMainFileConfig;
import org.zexnocs.teanekoagent.file_config.AgentMediaReplyFileConfig;
import org.zexnocs.teanekoagent.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoagent.file_config.personality.AgentPersonalityFileConfig;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMOptionBoundary;
import org.zexnocs.teanekoagent.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.personality.PersonalitySettings;
import org.zexnocs.teanekoagent.sticker.StickerLibrary;
import org.zexnocs.teanekoapp.web.files.FileSettings;
import org.zexnocs.teanekoapp.web.files.IFileSettingsAdapter;
import org.zexnocs.teanekoapp.web.files.TeaNekoFileSettings;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginOwned;

import java.util.List;
import java.util.Objects;

/**
 * Agent 各文件按模块贡献显式表单及原语义校验，不反射开放任意字段。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
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
    private static FileSettings.Rule field(String path, String label, String kind) {
        return new FileSettings.Rule(path, label, "保留原环境模板；设置后使用原模块范围校验。", kind, false);
    }

    /**
     * @param path 受保护原字段
     * @param label 中文名
     * @param kind 编辑类型
     * @return 只接受显式替换或清除的规则
     */
    private static FileSettings.Rule secret(String path, String label, String kind) {
        return new FileSettings.Rule(path, label, "不返回原值或展开值；留在保留状态不会改写，替换可填写环境占位符。", kind, true);
    }

    /**
     * 复用文件身份确认，具体模块补充实际消费端口检查。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private abstract static class Base implements IFileSettingsAdapter {
        final IFileConfigService files;
        private final Class<? extends IFileConfigData> type;
        private final String name;
        private final List<FileSettings.Rule> fields;

        /**
         * @param files 原加载器
         * @param type 固定文件类型
         * @param name 中文名
         * @param fields 明确字段
         */
        Base(IFileConfigService files, Class<? extends IFileConfigData> type, String name, List<FileSettings.Rule> fields) {
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
     * Agent 总开关表单，不借配置应用直接触发模型。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @Component
    @PluginOwned("teaneko.agent")
    @TeaNekoFileSettings("agent.main")
    public static final class Main extends Base {
        /** @param files 原文件入口 */
        public Main(IFileConfigService files) {
            super(files, AgentMainFileConfig.class, "Agent 总开关", List.of(field("/enabled", "启用 Agent", "BOOLEAN")));
        }
        /** @param candidate 原类型候选 */
        @Override
        public void validate(IFileConfigData candidate) {
            AgentMainFileConfig.class.cast(candidate);
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

    /**
     * 统一模型文件表单，凭据、连接及扩展参数只以保护字段替换。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @Component
    @PluginOwned("teaneko.agent")
    @TeaNekoFileSettings("agent.models")
    public static final class Models extends Base {
        private final ILLMFileConfigService models;
        /**
         * @param files 原文件入口
         * @param models 实际模型参数消费端口
         */
        public Models(IFileConfigService files, ILLMFileConfigService models) {
            super(files, LLMModelFileConfig.class, "模型、凭据与连接", List.of(
                    field("/default-model-id", "默认模型路由", "TEXT"), field("/models/*/id", "路由标识", "TEXT"),
                    field("/models/*/provider", "兼容供应商标识", "TEXT"), field("/models/*/model", "实际模型名称", "TEXT"),
                    secret("/models/*/api-key", "API 密钥", "TEXT"), secret("/models/*/base-url", "服务地址", "TEXT"),
                    secret("/models/*/api-path", "接口路径", "TEXT"), secret("/models/*/api", "旧接口路径别名", "TEXT"),
                    field("/models/*/thinking", "启用思考", "BOOLEAN"), field("/models/*/max-tokens", "最大输出 token", "NUMBER"),
                    field("/models/*/temperature", "采样温度", "NUMBER"), field("/models/*/top-p", "概率采样阈值", "NUMBER"),
                    field("/models/*/frequency-penalty", "频率惩罚", "NUMBER"), field("/models/*/presence-penalty", "存在惩罚", "NUMBER"),
                    field("/models/*/response-format", "响应格式", "JSON"), field("/models/*/stop-words", "停止词", "JSON"),
                    field("/models/*/stream", "流式响应选项", "BOOLEAN"), field("/models/*/tool-choice", "工具选择策略", "TEXT"),
                    field("/models/*/logprobs", "返回概率信息", "BOOLEAN"), secret("/models/*/metadata", "扩展参数 metadata", "JSON")));
            this.models = models;
        }
        /** @return 路由数组的真实增删能力 */
        @Override
        public List<FileSettings.CollectionRule> collections() {
            return List.of(new FileSettings.CollectionRule("/models", "模型路由", 64));
        }
        /**
         * @param document 未展开候选
         */
        @Override
        public void validateDocument(tools.jackson.databind.JsonNode document) {
            for (var model : document.path("models")) {
                if (model.hasNonNull("api") && model.hasNonNull("api-path")) {
                    throw new IllegalArgumentException("旧接口路径别名与 api-path 不能同时声明，请明确清除其中一个。");
                }
            }
        }
        /** @param candidate 原文件候选 */
        @Override
        public void validate(IFileConfigData candidate) {
            var file = LLMModelFileConfig.class.cast(candidate);
            var values = Objects.requireNonNull(file.getModels());
            if (values.size() > 64 || file.getDefaultModelId() == null
                    || !file.getDefaultModelId().isBlank() && file.findDefaultModelId().isEmpty()) {
                throw new IllegalArgumentException("模型路由数量或默认路由无效。");
            }
            for (var entry : values) {
                var id = Objects.requireNonNull(entry).findModelId().orElseThrow();
                file.findModelConfig(id).orElseThrow();
                if (entry.getModel() == null || entry.getModel().isBlank()) {
                    throw new IllegalArgumentException("模型条目必须填写实际模型名称。");
                }
                LLMOptionBoundary.validate(entry.toOptions(LLMModelOptions.builder().provider(id.id()).model(entry.getModel()).build()));
                new LLMProviderAccess(entry.getApiKey() == null || entry.getApiKey().isBlank() ? "仅本地格式校验" : entry.getApiKey(),
                        entry.getBaseUrl(), entry.getApiPath());
            }
            file.findDefaultModelId().ifPresent(id -> file.findModelConfig(id).orElseThrow());
        }
        /**
         * @param published 原候选
         * @return 实际模型配置消费端口的确认，不进行供应商请求
         */
        @Override
        public String verifyApplied(IFileConfigData published) {
            super.verifyApplied(published);
            if (models.getConfig() != published) {
                throw new IllegalStateException("模型参数端口没有读取到新快照。");
            }
            return "模型配置端口已读取新快照；旧已解析参数在换代后拒绝使用新凭据，已构造的 HTTP 请求保留原凭据。未发起模型请求验证密钥。";
        }
    }

    /**
     * 人格基础文件编辑，复用原基础人格冻结和引用检查。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @Component
    @PluginOwned("teaneko.agent")
    @TeaNekoFileSettings("agent.personality")
    public static final class Personality extends Base {
        private final ConfiguredPersonalities personalities;
        /**
         * @param files 原文件入口
         * @param personalities 原人格消费端口
         */
        public Personality(IFileConfigService files, ConfiguredPersonalities personalities) {
            super(files, AgentPersonalityFileConfig.class, "基础人格", List.of(
                    field("/default-agent-id", "默认人格标识", "TEXT"), field("/characters/*/id", "人格标识", "TEXT"),
                    field("/characters/*/display-name", "显示名称", "TEXT"), field("/characters/*/version", "人格版本", "NUMBER"),
                    field("/characters/*/identity/name", "角色名称", "TEXT"), field("/characters/*/identity/immutable", "固定身份约束", "JSON"),
                    field("/characters/*/core-traits", "核心特质", "JSON"), field("/characters/*/speaking-style", "表达风格", "JSON"),
                    field("/characters/*/boundaries/hard", "人格硬边界", "JSON"), field("/characters/*/boundaries/soft", "人格软偏好", "JSON"),
                    field("/characters/*/learning-policy/mutable-fields", "允许学习的表达字段", "JSON"),
                    field("/characters/*/learning-policy/immutable-fields", "禁止学习的字段", "JSON")));
            this.personalities = personalities;
        }
        /** @return 原人格列表的有界增删 */
        @Override
        public List<FileSettings.CollectionRule> collections() {
            return List.of(new FileSettings.CollectionRule("/characters", "人格定义", 100));
        }
        /**
         * @param id 明确人格或空默认
         * @return 无作用域覆盖的基础选择
         */
        private PersonalitySettings selection(String id) {
            return new PersonalitySettings(id, false, "", "", true, true);
        }
        /** @param candidate 原候选 */
        @Override
        public void validate(IFileConfigData candidate) {
            var file = AgentPersonalityFileConfig.class.cast(candidate);
            ConfiguredPersonalities.fromFile(file, selection(""));
            for (var definition : file.getCharacters()) {
                ConfiguredPersonalities.fromFile(file, selection(definition.getId()));
            }
        }
        /**
         * @param published 原候选
         * @return 默认基础人格消费端口确认
         */
        @Override
        public String verifyApplied(IFileConfigData published) {
            super.verifyApplied(published);
            String expected = ConfiguredPersonalities.fromFile(AgentPersonalityFileConfig.class.cast(published), selection("")).fingerprint();
            if (!expected.equals(personalities.base(selection("")).fingerprint())) {
                throw new IllegalStateException("人格端口没有读取到新基础配置。");
            }
            return "默认基础人格已由原消费端口确认；各聊天后续按原人格身份检查进行会话换代。";
        }
    }

    /**
     * 拟人分段和节奏设置，由实际表达编译端口确认。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @Component
    @PluginOwned("teaneko.agent")
    @TeaNekoFileSettings("agent.expression")
    public static final class Expression extends Base {
        private final ExpressionRuntime runtime;
        /**
         * @param files 原文件入口
         * @param runtime 原表达消费端口
         */
        public Expression(IFileConfigService files, ExpressionRuntime runtime) {
            super(files, AgentExpressionFileConfig.class, "拟人分段与发送节奏", List.of(
                    field("/enabled", "启用表达协议", "BOOLEAN"), field("/max-messages", "每轮最多发送条数", "NUMBER"),
                    field("/max-step-delay", "每段最大等待毫秒", "NUMBER"), field("/max-total-delay", "总等待上限毫秒", "NUMBER"),
                    field("/default-delay", "默认间隔毫秒", "NUMBER"), field("/timeout-seconds", "发送总超时秒", "NUMBER"),
                    field("/max-queued", "排队轮数上限", "NUMBER")));
            this.runtime = runtime;
        }
        /** @param candidate 原候选 */
        @Override
        public void validate(IFileConfigData candidate) {
            AgentExpressionFileConfig.class.cast(candidate).snapshot();
        }
        /**
         * @param published 原候选
         * @return 原表达运行时读取结果
         */
        @Override
        public String verifyApplied(IFileConfigData published) {
            super.verifyApplied(published);
            if (!runtime.settings().equals(AgentExpressionFileConfig.class.cast(published).snapshot())) {
                throw new IllegalStateException("表达编译端口没有读取到新设置。");
            }
            return "表达运行时已确认新设置，后续编译与准入使用新值；在途发送遵循原计划及守卫。";
        }
    }

    /**
     * 图库安全额度字段，后端身份和存储目录仍由本地管理。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @Component
    @PluginOwned("teaneko.agent")
    @TeaNekoFileSettings("agent.stickers")
    public static final class Stickers extends Base {
        private final StickerLibrary library;
        /**
         * @param files 原文件入口
         * @param library 原图库端口
         */
        public Stickers(IFileConfigService files, StickerLibrary library) {
            super(files, AgentStickerFileConfig.class, "表情包采集与容量", List.of(
                    field("/enabled", "启用图库", "BOOLEAN"), field("/collect-enabled", "启用采集", "BOOLEAN"),
                    field("/passive-enabled", "自动采集", "BOOLEAN"), field("/max-active", "有效表情包上限", "NUMBER"),
                    field("/max-per-category", "每类上限", "NUMBER"), field("/max-pending", "待处理上限", "NUMBER"),
                    field("/max-storage-bytes", "磁盘预算字节", "NUMBER"), field("/max-pending-bytes", "待处理预算字节", "NUMBER"),
                    field("/max-file-bytes", "单文件字节上限", "NUMBER"), field("/downloads-per-hour", "每小时下载数", "NUMBER"),
                    field("/scope-downloads-per-hour", "单聊天每小时下载数", "NUMBER"), field("/analyses-per-hour", "每小时分析数", "NUMBER"),
                    field("/scope-analyses-per-hour", "单聊天每小时分析数", "NUMBER"), field("/timeout-seconds", "分析超时秒", "NUMBER"),
                    field("/min-confidence", "最低表情置信度", "NUMBER"), field("/min-quality", "最低质量", "NUMBER"),
                    field("/probation-days", "保护天数", "NUMBER"), field("/replacement-margin", "替换分差", "NUMBER"),
                    field("/allowed-hosts", "允许来源主机", "JSON"), field("/categories", "类别描述", "JSON"),
                    field("/score-weights", "排序权重", "JSON")));
            this.library = library;
        }
        /** @param candidate 原候选 */
        @Override
        public void validate(IFileConfigData candidate) {
            AgentStickerFileConfig.class.cast(candidate).snapshot();
        }
        /**
         * @param candidate 原候选
         * @return 固定后端或定时器改变时要求重启，普通容量调整可后续使用
         */
        @Override
        public String applicationFor(IFileConfigData candidate) {
            var old = files.get(AgentStickerFileConfig.class);
            var next = AgentStickerFileConfig.class.cast(candidate);
            return Objects.equals(old.getStorageRoot(), next.getStorageRoot()) && Objects.equals(old.getCatalogId(), next.getCatalogId())
                    && Objects.equals(old.getAssets(), next.getAssets()) && Objects.equals(old.getCatalog(), next.getCatalog())
                    && old.getMaintenanceSeconds() == next.getMaintenanceSeconds() ? "NEXT_USE" : "RESTART";
        }
        /**
         * @param published 原候选
         * @return 原图库读取确认
         */
        @Override
        public String verifyApplied(IFileConfigData published) {
            super.verifyApplied(published);
            if (!library.settings().equals(AgentStickerFileConfig.class.cast(published).snapshot())) {
                throw new IllegalStateException("图库没有读取到新设置。");
            }
            return "图库已确认新额度与类别，后续工作使用新快照；当前文件和作业不因降低额度而立即删除。";
        }
    }

    /**
     * 媒体选图和客户端表情规则，原调用点逐次读取快照。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @Component
    @PluginOwned("teaneko.agent")
    @TeaNekoFileSettings("agent.media")
    public static final class Media extends Base {
        /** @param files 原文件入口 */
        public Media(IFileConfigService files) {
            super(files, AgentMediaReplyFileConfig.class, "选图与客户端表情", List.of(field("/enabled", "启用媒体表达", "BOOLEAN"),
                    field("/max-per-reply", "每轮表情包上限", "NUMBER"), field("/max-categories", "类别选择上限", "NUMBER"),
                    field("/cooldown-turns", "表情包冷却轮数", "NUMBER"), field("/max-category-characters", "类别说明字符上限", "NUMBER"),
                    field("/native-faces", "客户端表情映射", "JSON")));
        }
        /** @param candidate 原候选 */
        @Override
        public void validate(IFileConfigData candidate) {
            AgentMediaReplyFileConfig.class.cast(candidate).snapshot();
        }
    }
}
