package org.zexnocs.teanekoagent.adapter.web;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceBindings;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.settings.ChatSettings;
import org.zexnocs.teanekoapp.web.settings.IChatSettingsSection;
import org.zexnocs.teanekoapp.web.settings.TeaNekoChatSettings;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Agent 字段的中文业务投影，空间绑定保持专用管理边界，不经通用字段写入。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
@TeaNekoChatSettings("agent")
@PluginOwned("teaneko.agent")
public final class AgentChatSettingsSection implements IChatSettingsSection {
    private final ObjectMapper mapper;
    private final MemoryNamespaceBindings bindings;
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("enabled", "启用 Agent"), Map.entry("expressionEnabled", "拟人分段与节奏"),
            Map.entry("stickerCollectEnabled", "收集表情包"), Map.entry("stickerReplyEnabled", "使用表情包"),
            Map.entry("nativeExpressionsEnabled", "使用平台表情"), Map.entry("commandToolsEnabled", "开放命令工具"),
            Map.entry("skillsEnabled", "允许加载技能"), Map.entry("skills", "允许的技能名称"),
            Map.entry("historyCaptureEnabled", "记录聊天历史"), Map.entry("historyReadEnabled", "允许查询历史"),
            Map.entry("autoSummaryEnabled", "自动整理摘要"), Map.entry("memoryReadEnabled", "读取与注入记忆"),
            Map.entry("memoryAddEnabled", "允许本人纠正与遗忘"), Map.entry("memoryLearningEnabled", "自动学习记忆与画像"),
            Map.entry("personalityId", "基础人格"), Map.entry("customPersonalityEnabled", "启用自定义人格"),
            Map.entry("customPersonality", "自定义人格描述"), Map.entry("customSpeakingStyle", "自定义说话风格"),
            Map.entry("personalityCorrectionsEnabled", "应用人格补正"), Map.entry("personalityLearningEnabled", "观察学习表达习惯"),
            Map.entry("mode", "交互模式"), Map.entry("triggerNames", "机器人称呼"), Map.entry("taskTimeZone", "提醒任务时区"),
            Map.entry("activityTimeZone", "主动活动时区"), Map.entry("proactiveCooldownSeconds", "主动发言冷却秒数"),
            Map.entry("proactiveTimedEnabled", "定时主动发言"), Map.entry("proactiveInterjectionEnabled", "允许主动接话"),
            Map.entry("proactiveIntervalSeconds", "主动候选间隔秒数"), Map.entry("proactiveAttemptCooldownSeconds", "候选退避秒数"),
            Map.entry("proactiveQuietStartHour", "静默开始小时"), Map.entry("proactiveQuietEndHour", "静默结束小时"),
            Map.entry("historyMaxMessages", "短期历史消息上限"), Map.entry("inputMaxBytes", "模型输入字节上限"));

    /**
     * @param mapper 原转换器
     * @param bindings 权威共享空间绑定
     */
    public AgentChatSettingsSection(ObjectMapper mapper, MemoryNamespaceBindings bindings) {
        this.mapper = mapper;
        this.bindings = bindings;
    }

    /** @return 本业务中文名称 */
    @Override
    public String name() {
        return "Agent 聊天设置";
    }

    /** @return 原专用存储与字段校验声明 */
    @Override
    public ConfigManager manager() {
        return AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);
    }

    /**
     * @param target 原真实定位
     * @param snapshot 原子读取结果
     * @return 中文字段及只读共享绑定
     */
    @Override
    public CompletionStage<ChatSettings.View> view(ChatTargets.Resolved target, ConfigEdits.Snapshot snapshot) {
        var current = mapper.readTree(snapshot.json());
        var defaults = mapper.valueToTree(new AgentScopeConfigData());
        var fields = new ArrayList<ChatSettings.Field>();
        for (var field : AgentScopeConfigData.class.getDeclaredFields()) {
            String id = field.getName();
            if (!LABELS.containsKey(id)) {
                continue;
            }
            JsonNode value = current.has(id) ? current.get(id) : defaults.get(id);
            String kind = field.getType() == boolean.class ? "BOOLEAN"
                    : field.getType() == String.class ? "TEXT" : "NUMBER";
            if (id.equals("customPersonality") || id.equals("customSpeakingStyle")) {
                kind = "LONG_TEXT";
            }
            if (id.equals("mode")) {
                kind = "SELECT";
            }
            var description = field.getAnnotation(Description.class);
            String help = description == null ? "" : description.value().replace("当前作用域", "这个聊天");
            String source = !snapshot.registered() ? "登记后默认值" : current.has(id) ? "已保存" : "类型默认值";
            fields.add(new ChatSettings.Field(id, LABELS.get(id), group(id), kind, help,
                    text(value), text(defaults.get(id)), source, true, id.equals("mode")
                    ? Map.of("PASSIVE_BASIC", "仅明确呼叫时回复", "PASSIVE_CONTEXTUAL", "结合上下文被动回复", "ACTIVE", "主动交互")
                    : Map.of()));
        }
        return bindings.readReference(new AgentScope(target.clientId(), target.scope())).thenApply(reference -> {
            String namespace = reference.map(value -> value.namespaceId().value() + "（代次 " + value.generation() + "）")
                    .orElse("默认共享空间（首次使用时解析）");
            fields.add(new ChatSettings.Field("memoryNamespace", "记忆空间绑定", "记忆与历史",
                    "TEXT", "空间切换须使用专用记忆管理；这里不通过普通字段改写绑定。", namespace, "默认共享空间",
                    "权威绑定，只读", false, Map.of()));
            return new ChatSettings.View("agent", name(), target.name(), snapshot.registered(), snapshot.revision(),
                    target.kind().equals("PRIVATE") ? "同一个 TeaNeko 用户的私聊配置可能跨平台共享；修改会影响对应入口。"
                            : "同一平台的多个机器人账号可能共用这个群的配置；本次不更改既有配置隔离规则。",
                    List.copyOf(fields), null);
        });
    }

    /**
     * @param change 明确写请求
     * @throws IllegalArgumentException 当包含未开放字段时
     */
    @Override
    public void validate(ConfigEdits.Change change) {
        if (!LABELS.keySet().containsAll(change.fields().keySet())) {
            throw new IllegalArgumentException("请求包含本页面不可编辑的字段。");
        }
    }

    /**
     * @param node 原标量节点
     * @return 无 JSON 引号的字段文本
     */
    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.isString() ? node.asString() : node.toString();
    }

    /**
     * @param field 稳定字段标识
     * @return 中文业务分组
     */
    private static String group(String field) {
        if (field.startsWith("personality") || field.startsWith("custom")) {
            return "人格与表达";
        }
        if (field.startsWith("memory") || field.startsWith("history") || field.equals("autoSummaryEnabled")) {
            return "记忆与历史";
        }
        if (field.startsWith("proactive") || field.equals("activityTimeZone") || field.equals("taskTimeZone")) {
            return "活动与任务";
        }
        if (field.startsWith("sticker") || field.equals("nativeExpressionsEnabled") || field.equals("expressionEnabled")) {
            return "表达与素材";
        }
        return "Agent 与工具";
    }
}
