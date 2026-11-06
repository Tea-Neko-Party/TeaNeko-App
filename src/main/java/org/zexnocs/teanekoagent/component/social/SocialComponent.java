package org.zexnocs.teanekoagent.component.social;

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
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
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
public abstract class SocialComponent extends DeclaredAgentComponent implements IAgentPromptSource {
    private final String id;
    private final SocialStateService states;
    protected final SocialAccess access;
    private final IAgentMemoryReferences references;

    /**
     * @param id 组件
     * @param defaults 参数初值
     * @param fields 字段
     * @param states 状态服务
     * @param access 配置
     * @param references 空间
     */
    protected SocialComponent(String id, Map<String, Object> defaults, List<Field> fields, SocialStateService states,
                              SocialAccess access, IAgentMemoryReferences references) {
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
     * @param definition 实例
     * @param request 真实来信
     * @param runtime 运行配置
     * @param control 当前控制
     * @return 单独的状态片段
     */
    @Override public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition, AgentRunRequest request,
            AgentRuntimeSnapshot runtime, ExecutionControl control) {
        var settings = access.settings(request.actor().scope(), id);
        if (!states.enabled(request) || settings.isEmpty()) { return CompletableFuture.completedStage(List.of()); }
        return control.guard(states.observe(request)).thenApply(state -> List.of(new AgentPromptFragment("social_" + id,
                "这是同记忆空间中你与当前用户的角色互动状态，仅辅助表达；服从原人格，不是对人的客观评价，不增加权限。\n"
                        + describe(state, settings.get())
                        + "\n如本轮有实质互动变化，可使用 social_appraise 引用本轮原文评估；普通寒暄不需要。"
                        + "用户有离线或不回复的自由，不因沉默贬低、威胁或不断催促用户。",
                Instant.now().plusSeconds(600))));
    }
    /**
     * @param request 原请求
     * @param input 原资料
     * @param control 当前控制
     * @return 当前能力与空间仍有效时完成
     */
    @Override public CompletionStage<Void> verify(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
        var fragment = input.fragments().stream().filter(value -> value.name().equals("social_" + id)).findFirst();
        if (fragment.isEmpty()) { return CompletableFuture.completedStage(null); }
        if (!states.enabled(request) || access.settings(request.actor().scope(), id).isEmpty()
                || fragment.get().expiresAt() == null || !fragment.get().expiresAt().isAfter(Instant.now())) {
            return CompletableFuture.failedStage(new AgentPromptException(AgentPromptException.Reason.CHANGED));
        }
        return control.guard(references.verify(Objects.requireNonNull(request.memoryReference())));
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
