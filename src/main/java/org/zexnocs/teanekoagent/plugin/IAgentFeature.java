package org.zexnocs.teanekoagent.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.IEntitySystem;
import org.zexnocs.teanekocore.plugin.operation.OperationPipeline;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Agent 的可组合能力包；按需提供数据、System、工具、资料及异步准备拦截。
 * 默认不安装，注册 Bean 本身不授予实例或 scope 权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：增加模型调用前和交付前阶段，供组件提交各自的异步工作。
 *
 * <br>2026/11/04：增加模型调用前和最终交付前的可等待系统阶段。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/04：允许上层能力声明默认装配所需组件，底层不绑定具体客户端。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.0-l
 */
public interface IAgentFeature {
    /** @return 所属已注册插件 ID */
    String pluginId();
    /** @return 插件内局部能力 ID，使用小写字母、数字及短横线 */
    String id();
    /** @return 全局能力 ID，供 AgentDefinition 声明 */
    default String key() { return pluginId() + ":" + id(); }
    /** @return 自有组件及 System 的稳定命名空间 */
    default String componentPrefix() { return "plugin." + pluginId() + "." + id() + "."; }
    /** @return 是否由宿主默认安装，第三方应保持假 */
    default boolean defaultEnabled() { return false; }
    /**
     * 2026/11/04：声明默认能力自动接入新装配所需的全部组件；空集不触发此选择规则。
     * 显式能力选择与旧装配仍按原权限和依赖规则执行。
     * @return 稳定组件标识集合
     */
    default Set<String> assemblyRequirements() {
        return Set.of();
    }
    /** @return 需要同时安装的能力 ID */
    default Set<String> dependencies() { return Set.of(); }
    /** @return 明确允许初始化及写入的自有组件 */
    default Set<ComponentKey<?>> components() { return Set.of(); }
    /**
     * 创建一次实例级不可变数据，不访问存储或启动业务。
     * @param definition 当前定义
     * @return 自有组件初始值
     */
    default ComponentPatch instanceComponents(AgentDefinition definition) { return ComponentPatch.empty(); }
    /**
     * 在准入后创建轮次独立数据，不将会话状态写入单例字段。
     * @param context 本轮可信上下文
     * @return 自有轮次初始值
     */
    default ComponentPatch turnComponents(AgentPluginContext context) { return ComponentPatch.empty(); }
    /** @return 受控阶段中的 System 声明 */
    default List<SystemContribution> systems() { return List.of(); }
    /** @return 明确的工具源，默认没有工具 */
    default IAgentToolSource tools() { return (definition, request, runtime) -> List.of(); }
    /** @return 可等待资料源，默认不注入资料 */
    default IAgentPromptSource prompts() { return IAgentPromptSource.empty(); }
    /** @return 此能力拥有的资料来源名，解析、拦截器及事件新增资料均须预先声明 */
    default Set<String> promptNames() { return Set.of(); }
    /**
     * 提供资料解析操作的异步环绕，不得更换可信请求或固定控制。
     * @return 属于本插件的有序拦截节点
     */
    default List<OperationPipeline.Binding<AgentPluginContext, List<AgentPromptFragment>>> promptInterceptors() { return List.of(); }

    /**
     * 宿主允许扩展的阶段，不包含准入、模型循环及最终历史提交的替换。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：扩充输入采集与交付准备阶段，保留统一准入守卫。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.8.0-l
     */
    // 2026/11/04：组件可在输入固定后及交付前等待持久化；所有阶段仍受宿主准入守卫。
    enum Phase { BEFORE_PROMPT, BEFORE_INTERACTION, AFTER_INTERACTION, BEFORE_DELIVERY }

    /**
     * 将普通 Core System 安排到明确业务阶段。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param phase 执行阶段
     * @param system 声明输入、写入及依赖的无状态 System
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    record SystemContribution(Phase phase, IEntitySystem system) {
        /**
         * 拒绝缺失的执行阶段与实现。
         * @param phase 宿主阶段
         * @param system System 实现
         */
        public SystemContribution { Objects.requireNonNull(phase); Objects.requireNonNull(system); }
    }
}
