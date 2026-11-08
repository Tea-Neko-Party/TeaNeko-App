package org.zexnocs.teanekoplugin.agent.example;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.tool.LLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.llm.framework.tool.parameter.AbstractLLMFunctionParameter;
import org.zexnocs.teanekoagent.plugin.IAgentFeature;
import org.zexnocs.teanekoagent.plugin.AgentPluginContext;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekocore.framework.composition.*;
import org.zexnocs.teanekocore.plugin.operation.OperationPipeline;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 完整示例能力：实例时区组件、轮次时间组件、System、工具及异步资料环绕。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * <br>2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.9.0-l
 */
@PluginOwned("example.clock")
public final class ExampleClockFeature implements IAgentFeature {
    public static final String KEY = "example.clock:clock";
    public static final ComponentKey<String> ZONE = ComponentKey.of("plugin.example.clock.clock.zone", String.class);
    public static final ComponentKey<Instant> STARTED_AT = ComponentKey.of("plugin.example.clock.clock.started-at", Instant.class);
    private final Clock clock;
    /**
     * 借用时钟，插件不创建执行器或数据库。
     * @param clock 宿主时钟
     */
    public ExampleClockFeature(Clock clock) { this.clock = Objects.requireNonNull(clock); }
    /** @return 所属插件 */
    @Override public String pluginId() { return "example.clock"; }
    /** @return 局部能力 */
    @Override public String id() { return "clock"; }
    /** @return 本能力自有参考资料名 */
    @Override public Set<String> promptNames() { return Set.of("example_clock"); }
    /** @return 允许写入的自有组件 */
    @Override public Set<ComponentKey<?>> components() { return Set.of(ZONE, STARTED_AT); }
    /**
     * 保存实例不可变展示时区。
     * @param definition 实例定义
     * @return 实例配置组件
     */
    @Override public ComponentPatch instanceComponents(AgentDefinition definition) { return ComponentPatch.builder().put(ZONE, "Asia/Shanghai").build(); }
    /** @return 准入后执行的资料准备 System */
    @Override public List<SystemContribution> systems() { return List.of(new SystemContribution(Phase.BEFORE_PROMPT, new ClockSystem(clock))); }
    /**
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @return 没有参数的真实工具绑定，时区来自可信组件
     */
    @Override public IAgentToolSource tools() {
        var definition = LLMTool.builder().name("example_clock_now").description("查询当前实例时区的时间。")
                .parameters(AbstractLLMFunctionParameter.object(null, Map.of(), List.of())).build();
        var binding = new LLMToolBinding(definition, (call, trusted) -> {
            trusted.getControl().checkActive(); var zone = ZoneId.of(trusted.bindings().require(ZONE));
            var started = trusted.bindings().require(STARTED_AT);
            return CompletableFuture.completedStage("当前时间：" + clock.instant().atZone(zone) + "；本轮开始：" + started.atZone(zone));
        });
        return (agent, request, runtime) -> List.of(binding);
    }
    /**
     * 在完整资料解析后追加独立低信任参考项，返回转换后的阶段。
     * @return 本能力所属的异步环绕
     */
    @Override public List<OperationPipeline.Binding<AgentPluginContext, List<AgentPromptFragment>>> promptInterceptors() {
        return List.of(new OperationPipeline.Binding<>(pluginId(), "clock-note", 0, (context, next) -> next.execute(context).thenApply(values -> {
            var result = new ArrayList<>(values);
            result.add(new AgentPromptFragment("example_clock", "本轮参考时刻：" + context.snapshot().require(STARTED_AT)));
            return List.copyOf(result);
        })));
    }

    /**
     * 无状态 System；每个轮次单独写入不可变时间，单例不保存用户状态。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param clock 借用时钟
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    private record ClockSystem(Clock clock) implements IEntitySystem {
        /** @return 必需组件、自有写入和明确顺序 */
        @Override public SystemDescriptor descriptor() {
            return new SystemDescriptor("plugin.example.clock.clock.prepare", Set.of(ZONE), Set.of(STARTED_AT), Set.of());
        }
        /**
         * 写入本轮开始时刻。
         * @param context 本轮只读组件及借用控制
         * @return 不直接提交实体的组件补丁
         */
        @Override public CompletionStage<SystemResult> execute(SystemContext context) {
            context.control().checkActive();
            return CompletableFuture.completedStage(SystemResult.continueWith(ComponentPatch.builder().put(STARTED_AT, clock.instant()).build()));
        }
    }
}
