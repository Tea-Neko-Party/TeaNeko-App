package org.zexnocs.teanekoplugin.agent.cognition.integration.web;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.integration.web.AgentFileSettings.Base;
import org.zexnocs.teanekoapp.web.files.FileSettings;
import org.zexnocs.teanekoapp.web.files.TeaNekoFileSettings;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalitySettings;
import org.zexnocs.teanekoplugin.agent.cognition.personality.adapter.ConfiguredPersonalities;
import org.zexnocs.teanekoplugin.agent.cognition.personality.configuration.file_config.AgentPersonalityFileConfig;

import java.util.List;

import static org.zexnocs.teanekoagent.integration.web.AgentFileSettings.field;

/**
 * 认知插件的本地配置贡献，停用后由统一目录隐藏。
 * <br>2026/11/07：按真实插件归属拆分原文件设置。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class CognitionFileSettings {
    /** 禁止实例化配置贡献命名空间。 */
    private CognitionFileSettings() {}

    /**
     * 人格基础文件编辑，复用原基础人格冻结和引用检查。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    @Component
    @PluginOwned("agent.cognition")
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
        /**
         * 2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
         * @param candidate 原候选
         */
        @Override
        public void validate(IFileConfigData candidate) {
            var file = (AgentPersonalityFileConfig) candidate;
            ConfiguredPersonalities.fromFile(file, selection(""));
            for (var definition : file.getCharacters()) {
                ConfiguredPersonalities.fromFile(file, selection(definition.getId()));
            }
        }
        /**
         * 2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
         * @param published 原候选
         * @return 默认基础人格消费端口确认
         */
        @Override
        public String verifyApplied(IFileConfigData published) {
            super.verifyApplied(published);
            String expected = ConfiguredPersonalities.fromFile(((AgentPersonalityFileConfig) published), selection("")).fingerprint();
            if (!expected.equals(personalities.base(selection("")).fingerprint())) {
                throw new IllegalStateException("人格端口没有读取到新基础配置。");
            }
            return "默认基础人格已由原消费端口确认；各聊天后续按原人格身份检查进行会话换代。";
        }
    }
}
