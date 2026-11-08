package org.zexnocs.teanekoplugin.agent.cognition.personality.adapter;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekoplugin.agent.cognition.personality.BasePersonality;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IPersonalityConfiguration;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalitySettings;
import org.zexnocs.teanekoplugin.agent.cognition.personality.configuration.file_config.AgentPersonalityDefinition;
import org.zexnocs.teanekoplugin.agent.cognition.personality.configuration.file_config.AgentPersonalityFileConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。
// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 从现有文件与真实作用域登记读取人格，缺失/损坏配置不会静默选择其他角色。
 * <br>2026/11/03：抽出基础人格候选校验，网页预检复用同一规则。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：读取当前聊天人格组件的安装状态。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：从已应用本地配置发布基础人格目录，不依赖伪聊天配置。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：业务参数由所属组件解释，实时复核装配与插件资格。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class ConfiguredPersonalities implements IPersonalityConfiguration {
    private final AgentComponentAccess components;
    private final IFileConfigService files;
    /**
     * 保存原配置端口，不在构造时加载文件或数据库。
     * @param components 当前有效组件参数
     * @param files 原文件配置服务
     */
    public ConfiguredPersonalities(AgentComponentAccess components, IFileConfigService files) {
        this.components = components;
        this.files = Objects.requireNonNull(files, "人格文件配置服务不能为空。");
    }
    /**
     * 只使用真实已启用记录，独立固定应用与学习开关。
     * @param scope 真实作用域
     * @return 本轮配置
     */
    @Override public PersonalitySettings settings(AgentScope scope) {
        var parameters = components.settings(scope, "personality")
                .orElseThrow(() -> new AgentPromptException(AgentPromptException.Reason.DISABLED));
        return new PersonalitySettings(parameters.path("personalityId").asString(""),
                parameters.path("customPersonalityEnabled").asBoolean(false),
                parameters.path("customPersonality").asString(""),
                parameters.path("customSpeakingStyle").asString(""),
                parameters.path("personalityCorrectionsEnabled").asBoolean(true),
                parameters.path("personalityLearningEnabled").asBoolean(true));
    }

    /**
     * @param scope 真实聊天定位
     * @return 2026/11/04：人格的显式装配状态，未登记不初始化人格
     */
    @Override
    public boolean installed(AgentScope scope) {
        return components.settings(scope, "personality").isPresent();
    }
    /**
     * 冻结旧布局的基础人格并严格检查重复 ID；仅空旧列表的默认 teaneko 使用明确内置设定。
     * @param settings 本轮作用域选择
     * @return 完整有界基础人格
     * @throws IllegalArgumentException 当配置、目标或 ID 不合法时
     */
    @Override public BasePersonality base(PersonalitySettings settings) {
        var file = Objects.requireNonNull(files.get(AgentPersonalityFileConfig.class), "人格文件未加载。");
        // 2026/11/03：预检候选与实际读取共享同一冻结逻辑。
        return fromFile(file, settings);
    }

    /** @return 已应用本地文件的基础人格目录，不读取任何聊天配置。 */
    @Override
    public List<BasePersonality> catalog() {
        if (!components.available("personality")) {
            return List.of();
        }
        var file = files.requireCurrent(AgentPersonalityFileConfig.class);
        var ids = file.getCharacters().isEmpty() ? List.of(file.getDefaultAgentId())
                : file.getCharacters().stream().map(AgentPersonalityDefinition::getId).toList();
        return ids.stream().map(id -> fromFile(file,
                new PersonalitySettings(id, false, "", "", true, false))).toList();
    }

    /**
     * 2026/11/03：只冻结传入候选，不读取或发布全局缓存。
     * @param file 原文件候选
     * @param settings 明确人格选择
     * @return 有界基础人格
     */
    public static BasePersonality fromFile(AgentPersonalityFileConfig file, PersonalitySettings settings) {
        var defaultId = Objects.requireNonNull(file.getDefaultAgentId(), "默认人格 ID 不能为空。").strip();
        var id = settings.personalityId().isBlank() ? defaultId : settings.personalityId().strip();
        var characters = List.copyOf(Objects.requireNonNull(file.getCharacters(), "人格列表不能为空。"));
        if (defaultId.isBlank() || characters.size() > 100 || characters.stream().map(AgentPersonalityDefinition::getId).distinct().count() != characters.size()) {
            throw new IllegalArgumentException("人格默认值、数量或重复 ID 配置无效。");
        }
        var rules = new ArrayList<String>(); var mutable = new HashSet<String>(); String name; int version;
        if (characters.isEmpty() && id.equals("teaneko")) {
            name = "TeaNeko"; version = 1;
            rules.add("保持 TeaNeko 的身份；自然、直接地交流，优先帮助用户解决当前问题。");
            rules.add("不虚构记忆、工具执行或现实能力；资料不足时明确说明。");
            mutable.add("speaking detail"); mutable.add("relationship");
        } else {
            var definition = characters.stream().filter(item -> id.equals(item.getId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("配置所选基础人格不存在。"));
            name = definition.getIdentity().getName().isBlank() ? definition.getDisplayName() : definition.getIdentity().getName(); version = definition.getVersion();
            rules.add("基础身份约束（不可学习覆盖）："); rules.addAll(definition.getIdentity().getImmutable());
            rules.add("人格硬边界（低于宿主运行规则）："); rules.addAll(definition.getBoundaries().getHard());
            if (!settings.customEnabled()) {
                rules.add("核心特质："); rules.addAll(definition.getCoreTraits()); rules.add("基础表达风格："); rules.addAll(definition.getSpeakingStyle());
                rules.add("可调整的软偏好："); rules.addAll(definition.getBoundaries().getSoft());
            }
            mutable.addAll(definition.getLearningPolicy().getMutableFields()); definition.getLearningPolicy().getImmutableFields().forEach(mutable::remove);
        }
        if (settings.customEnabled()) {
            rules.add(settings.customPersonality()); if (!settings.customSpeakingStyle().isBlank()) { rules.add(settings.customSpeakingStyle()); }
        }
        return new BasePersonality(id, version, name, rules, mutable);
    }
}
