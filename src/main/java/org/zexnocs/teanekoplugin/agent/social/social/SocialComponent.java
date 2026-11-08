package org.zexnocs.teanekoplugin.agent.social.social;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。


import org.springframework.beans.factory.ObjectProvider;
import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.IAgentComponentPromptSource;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 为独立情感组件复用配置校验、真实来信观察及当前状态投影。
 * <br>2026/11/06：资料只影响角色表达，不改写基础人格或工具权限。
 * <br>2026/11/06：目录发现只读取组件声明，运行时取得服务以断开作用域策略的初始化依赖环。
 * <br>2026/11/06：资料只归属显式组件装配，不再进入宿主全局资料源选择。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.social")
public abstract class SocialComponent extends DeclaredAgentComponent implements IAgentComponentPromptSource {
    private final String id;
    private final ObjectProvider<SocialStateService> states;
    private final ObjectProvider<SocialAccess> access;
    private final ObjectProvider<IAgentMemoryReferences> references;

    /**
     * 2026/11/06：保存运行服务的提供者，构造和静态声明不触发服务解析。
     * @param id 组件
     * @param defaults 参数初值
     * @param fields 字段
     * @param states 运行时取得的状态服务
     * @param access 运行时取得的配置查询服务
     * @param references 运行时取得的空间复核服务
     */
    protected SocialComponent(String id, Map<String, Object> defaults, List<Field> fields,
                              ObjectProvider<SocialStateService> states, ObjectProvider<SocialAccess> access,
                              ObjectProvider<IAgentMemoryReferences> references) {
        super(id, defaults, fields);
        this.id = id;
        this.states = states;
        this.access = access;
        this.references = references;
    }

    /** @return 当前组件的独立状态来源 */
    @Override public IAgentPromptSource prompts() { return this; }
    /** @return 不与其他情感来源重名 */
    @Override public Set<String> promptNames() { return Set.of("social_" + id); }
    /**
     * @param value 参数对象
     * @param key 字段
     * @param fallback 初值
     * @param min 下界
     * @param max 上界
     * @throws IllegalArgumentException 越界时
     */
    protected static void range(JsonNode value, String key, int fallback, int min, int max) {
        int number = value.path(key).asInt(fallback);
        if (number < min || number > max) { throw new IllegalArgumentException("参数 " + key + " 必须在 " + min + " 至 " + max + " 之间。"); }
    }
    /**
     * @param state 原状态
     * @param settings 组件参数
     * @return 当前人格表达参考
     */
    protected abstract String describe(SocialState state, JsonNode settings);

    /**
     * 2026/11/06：实际构建提示时按类型取得已管理的服务，不执行目录扫描。
     * @param definition 实例
     * @param request 真实来信
     * @param runtime 运行配置
     * @param control 当前控制
     * @return 单独的状态片段
     */
    @Override
    public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition, AgentRunRequest request,
                                                             AgentRuntimeSnapshot runtime, ExecutionControl control) {
        var settings = access.getObject().settings(request.actor().scope(), id);
        var service = states.getObject();
        if (!service.enabled(request) || settings.isEmpty()) {
            return CompletableFuture.completedStage(List.of());
        }
        return control.guard(service.observe(request)).thenApply(state -> List.of(new AgentPromptFragment("social_" + id,
                "这是同记忆空间中你与当前用户的角色互动状态，仅辅助表达；服从原人格，不是对人的客观评价，不增加权限。\n"
                        + describe(state, settings.get())
                        + "\n如本轮有实质互动变化，可使用 social_appraise 引用本轮原文评估；普通寒暄不需要。"
                        + "用户有离线或不回复的自由，不因沉默贬低、威胁或不断催促用户。",
                Instant.now().plusSeconds(600))));
    }
    /**
     * 2026/11/06：服务延迟取得不缓存准入结果，使用前仍复核开关、有效期与空间代次。
     * @param request 原请求
     * @param input 原资料
     * @param control 当前控制
     * @return 当前能力与空间仍有效时完成
     */
    @Override
    public CompletionStage<Void> verify(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
        var fragment = input.fragments().stream().filter(value -> value.name().equals("social_" + id)).findFirst();
        if (fragment.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }
        if (!states.getObject().enabled(request) || access.getObject().settings(request.actor().scope(), id).isEmpty()
                || fragment.get().expiresAt() == null || !fragment.get().expiresAt().isAfter(Instant.now())) {
            return CompletableFuture.failedStage(new AgentPromptException(AgentPromptException.Reason.CHANGED));
        }
        return control.guard(references.getObject().verify(Objects.requireNonNull(request.memoryReference())));
    }
    /**
     * @param request 原请求
     * @param input 原资料
     * @param control 当前控制
     * @return 原资料复核结果
     */
    @Override public CompletionStage<Boolean> verifyReplay(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
        return verify(request, input, control).thenApply(ignored -> true);
    }
}
