package org.zexnocs.teanekoagent.adapter.personality;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.file_config.personality.AgentPersonalityDefinition;
import org.zexnocs.teanekoagent.file_config.personality.AgentPersonalityFileConfig;
import org.zexnocs.teanekoagent.personality.BasePersonality;
import org.zexnocs.teanekoagent.personality.IPersonalityConfiguration;
import org.zexnocs.teanekoagent.personality.PersonalitySettings;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 从现有文件与真实作用域登记读取人格，缺失/损坏配置不会静默选择其他角色。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class ConfiguredPersonalities implements IPersonalityConfiguration {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final IFileConfigService files;
    /**
     * 保存原配置端口，不在构造时加载文件或数据库。
     * @param configs 原作用域配置服务
     * @param manager 原 Agent 注册声明
     * @param files 原文件配置服务
     */
    public ConfiguredPersonalities(IConfigDataService configs, AgentScopeConfigManager manager, IFileConfigService files) {
        this.configs = Objects.requireNonNull(configs, "作用域配置服务不能为空。"); this.manager = Objects.requireNonNull(manager, "Agent 配置声明不能为空。"); this.files = Objects.requireNonNull(files, "人格文件配置服务不能为空。");
    }
    /**
     * 只使用真实已启用记录，独立固定应用与学习开关。
     * @param scope 真实作用域
     * @return 本轮配置
     */
    @Override public PersonalitySettings settings(AgentScope scope) {
        var data = configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                .orElseThrow(() -> new AgentPromptException(AgentPromptException.Reason.DISABLED));
        if (!data.toPolicy().enabled()) { throw new AgentPromptException(AgentPromptException.Reason.DISABLED); }
        return new PersonalitySettings(data.getPersonalityId(), data.isCustomPersonalityEnabled(), data.getCustomPersonality(),
                data.getCustomSpeakingStyle(), data.isPersonalityCorrectionsEnabled(), data.isPersonalityLearningEnabled());
    }
    /**
     * 冻结旧布局的基础人格并严格检查重复 ID；仅空旧列表的默认 teaneko 使用明确内置设定。
     * @param settings 本轮作用域选择
     * @return 完整有界基础人格
     * @throws IllegalArgumentException 当配置、目标或 ID 不合法时
     */
    @Override public BasePersonality base(PersonalitySettings settings) {
        var file = Objects.requireNonNull(files.get(AgentPersonalityFileConfig.class), "人格文件未加载。");
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
            mutable.addAll(definition.getLearningPolicy().getMutableFields()); mutable.removeAll(definition.getLearningPolicy().getImmutableFields());
        }
        if (settings.customEnabled()) {
            rules.add(settings.customPersonality()); if (!settings.customSpeakingStyle().isBlank()) { rules.add(settings.customSpeakingStyle()); }
        }
        return new BasePersonality(id, version, name, rules, mutable);
    }
}
