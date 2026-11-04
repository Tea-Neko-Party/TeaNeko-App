package org.zexnocs.teanekoagent.integration.web;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceBindings;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentScope;
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
 * <br>2026/11/04：专用投影归入 Agent 分组，记忆绑定仍不经通用字段改写。
 *
 * <br>2026/11/04：增加已应用模型路由选择，保留原配置版本与权限复核。
 *
 * <br>2026/11/04：使用目录驱动的装配表单及脱敏编辑，替代重复平铺字段。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：开放失败原因回复的中文配置字段与版本校验。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.2-l
 */
@Component
// 2026/11/04：Agent 专用投影明确归入独立配置分组。
@TeaNekoChatSettings(value = "agent", group = "agent")
@PluginOwned("teaneko.agent")
public final class AgentChatSettingsSection implements IChatSettingsSection {
    private org.zexnocs.teanekoagent.component.AgentComponentRegistry components;

    /**
     * 2026/11/04：表单使用真实组件目录及秘密保留协议。
     * @param components 已注册的组件目录
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installComponents(org.zexnocs.teanekoagent.component.AgentComponentRegistry components) {
        this.components = components;
    }
    private final ObjectMapper mapper;
    private final MemoryNamespaceBindings bindings;
    // 2026/11/05：失败原因回复与启用、触发方式一样属于聊天入口策略。
    private static final Map<String, String> LABELS = Map.of("enabled", "启用 Agent", "mode", "交互模式",
            "triggerNames", "机器人称呼", "inputMaxBytes", "模型输入字节上限", "failureReplyEnabled", "失败原因回复");

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
        // 2026/11/04：装配表单取代相互重叠的旧字段，历史记录只生成等价预览。
        fields.add(new ChatSettings.Field("components", "组件装配", "Agent 组件", "ASSEMBLY",
                "LLM 必需；其余能力按需安装，每个组件拥有独立参数。", components.editor(snapshot.json()),
                org.zexnocs.teanekoagent.component.AgentAssembly.MINIMAL, "版本化装配", true, Map.of()));
        for (var field : AgentScopeConfigData.class.getDeclaredFields()) {
            String id = field.getName();
            // 2026/11/05：允许在网页直接调整失败回执，不需重新装配任何组件。
            if (!java.util.Set.of("enabled", "mode", "triggerNames", "inputMaxBytes", "failureReplyEnabled").contains(id)) {
                continue;
            }
            JsonNode value = current.has(id) ? current.get(id) : defaults.get(id);
            String kind = field.getType() == boolean.class ? "BOOLEAN"
                    : field.getType() == String.class ? "TEXT" : "NUMBER";
            if (id.equals("mode")) {
                kind = "SELECT";
            }
            var description = field.getAnnotation(Description.class);
            String help = description == null ? "" : description.value().replace("当前作用域", "这个聊天");
            String source = !snapshot.registered() ? "登记后默认值" : current.has(id) ? "已保存" : "类型默认值";
            fields.add(new ChatSettings.Field(id, LABELS.get(id), "聊天接入", kind, help,
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
        // 2026/11/05：失败回复是聊天入口策略，可独立于组件装配修改。
        if (!java.util.Set.of("enabled", "mode", "triggerNames", "inputMaxBytes", "components", "failureReplyEnabled")
                .containsAll(change.fields().keySet())) {
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

}
