package org.zexnocs.teanekoagent.integration.config;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 已加载模型的公开路由选择，命令与网页共享同一选择集，不导出密钥及连接。
 *
 * 修改记录：<br>
 * 2026/11/04：提供聊天级模型选择和版本化配置提交。
 *
 * <br>2026/11/04：系统路由切换只替换 LLM 组件，保留其余装配。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.7-l
 * @version 4.8.0-l
 */
@Service
public final class AgentModelSelection {
    private final ILLMFileConfigService files;
    private final ILLMModelService models;
    private final ConfigEditService edits;

    /**
     * @param files 当前已应用的模型文件
     * @param models 实际注册模型
     * @param edits 原配置事务
     */
    public AgentModelSelection(ILLMFileConfigService files, ILLMModelService models, ConfigEditService edits) {
        this.files = files;
        this.models = models;
        this.edits = edits;
    }

    /**
     * @return 唯一路由及具体模型名，不包含秘密
     */
    public Map<String, String> choices() {
        var config = files.getConfig();
        var choices = new LinkedHashMap<String, String>();
        choices.put("", "继承默认模型（" + config.getDefaultModelId() + "）");
        for (var entry : config.getModels()) {
            var id = entry.findModelId().orElseThrow();
            config.findModelConfig(id).orElseThrow();
            choices.put(id.id(), id.id() + " · " + entry.getModel());
        }
        return choices;
    }

    /**
     * @param route 空值继承默认，否则必须已经配置且实际注册
     * @throws IllegalArgumentException 当路由不存在时
     */
    public void require(String route) {
        if (!choices().containsKey(route)) {
            throw new IllegalArgumentException("请选择已经添加并应用的模型路由。");
        }
        if (!route.isEmpty()) {
            org.zexnocs.teanekoagent.llm.framework.model.LLMOptionBoundary.validate(
                    models.getDefaultOptions(org.zexnocs.teanekoagent.llm.framework.model.LLMModelId.of(route)));
        }
    }

    /**
     * OWNER 命令经原事务和当前身份复核切换聊天模型，不自动登记或重发旧请求。
     * @param data 原真实消息及权限
     * @param route 空值或已配置路由
     * @param control 可选借用的上游执行控制
     * @return 提交结果
     */
    public CompletionStage<String> select(ITeaNekoMessageData data, String route,
            org.zexnocs.teanekocore.actuator.execution.@org.jspecify.annotations.Nullable ExecutionControl control) {
        Runnable guard = () -> {
            if (control != null) {
                control.checkActive();
            }
            if (!java.util.Set.of(CommandPermission.OWNER, CommandPermission.DEBUG).contains(data.getUserData().getRole())) {
                throw new IllegalStateException("切换聊天模型需要 OWNER 权限。");
            }
            require(route);
        };
        guard.run();
        var manager = AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);
        var stage = edits.read(manager, data.getScopeId(), guard).thenCompose(snapshot -> {
            if (!snapshot.registered()) {
                throw new IllegalStateException("请先登记当前聊天的 Agent 配置。");
            }
            var operation = UUID.nameUUIDFromBytes(org.zexnocs.teanekocore.utils.StableDigest.sha256(
                    data.getClient().getClientId(), data.getScopeId(), data.getMessageId(), "model", route)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // 2026/11/04：旧记录沿用原字段，新装配只替换 LLM 组件，不丢失其他组件参数。
            var stored = new tools.jackson.databind.ObjectMapper().readTree(snapshot.json()).path("components");
            Map<String, String> fields;
            if (stored.isString()) {
                var document = org.zexnocs.teanekoagent.component.AgentAssembly.document(stored.asString());
                var installed = (tools.jackson.databind.node.ObjectNode) document.path("components");
                installed.putObject("llm").put("source", "system").put("route", route);
                fields = Map.of("components", document.toString());
            } else {
                fields = Map.of("modelRoute", route);
            }
            var change = new ConfigEdits.Change(operation, snapshot.revision(), ConfigEdits.Action.PATCH, fields);
            return edits.change(manager, data.getScopeId(), data.getUserData().getUuid().toString(), change, guard);
        }).thenApply(receipt -> "聊天模型已切换为：" + (route.isEmpty() ? "继承默认模型" : route) + "；从新请求开始生效。");
        return control == null ? stage : control.guard(stage);
    }
}
