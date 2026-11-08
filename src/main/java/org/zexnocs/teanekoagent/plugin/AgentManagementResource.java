package org.zexnocs.teanekoagent.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.runtime.IAgentRegistry;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.plugin.management.IManagementResource;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 提供已注册实例及能力的脱敏全局管理视图，不反射导出组件值、记忆或运行控制。
 * 全局实例查看需要宿主明确授予 agent.inspect 权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：组件数据引用迁入 component，保持原业务流程。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.0-l
 */
@Component
public final class AgentManagementResource implements IManagementResource<AgentManagementResource.Query, List<AgentManagementResource.View>> {
    private final IAgentRegistry registry;
    private final AgentPluginRuntime plugins;
    /**
     * 借用实例和能力目录。
     * @param registry 实例目录
     * @param plugins 能力目录
     */
    public AgentManagementResource(IAgentRegistry registry, AgentPluginRuntime plugins) { this.registry = registry; this.plugins = plugins; }
    /** @return 受全局查看权限保护的只读资源 */
    @Override public Descriptor descriptor() {
        return new Descriptor("teaneko.agent", "instances", 1, "agent.inspect", false, List.of(
                new Field("after", "string", "原页面末项实例ID，首屏可省略。", false, false, List.of()),
                new Field("limit", "integer", "单页条数，一至一百。", true, false, List.of())));
    }
    /** @return 有界分页请求类型 */
    @Override public Class<Query> requestType() { return Query.class; }
    /**
     * 按稳定实例 ID 排序，返回指定游标之后的有界视图。
     * @param context 已授权上下文
     * @param query 游标及页大小
     * @return 不含敏感字段的实例列表，末项 ID 可作后继游标
     */
    @Override public CompletionStage<List<View>> execute(ManagementContext context, Query query) {
        context.control().checkActive();
        return CompletableFuture.completedStage(registry.snapshots().stream().sorted(Comparator.comparing(value -> value.id().value()))
                .filter(value -> query.after() == null || value.id().value().compareTo(query.after()) > 0).limit(query.limit())
                .map(value -> {
                    var definition = value.require(AgentComponents.DEFINITION);
                    return new View(value.id(), definition.id(), definition.features(), plugins.featureIds(), value.require(AgentComponents.CLOSED));
                }).toList());
    }
    /**
     * 有界实例查询。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param after 原排序游标，首屏为空
     * @param limit 页大小，一至一百
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    public record Query(String after, int limit) {
        /**
         * 拒绝无界页面。
         * @param after 游标
         * @param limit 页大小
         */
        public Query { if (limit < 1 || limit > 100) { throw new IllegalArgumentException("实例页大小必须为一至一百。"); } }
    }
    /**
     * 实例基础视图，已声明能力与全局可安装目录分别展示。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 实例 ID
     * @param definitionId 定义 ID
     * @param declaredFeatures 实例显式声明
     * @param availableFeatures 已注册目录，不表示全部启用或有权执行
     * @param closed 实例关闭标记
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    public record View(EntityId id, String definitionId, Set<String> declaredFeatures, Set<String> availableFeatures, boolean closed) {
        /**
         * 冻结能力视图。
         * @param id 实例
         * @param definitionId 定义
         * @param declaredFeatures 显式声明
         * @param availableFeatures 注册目录
         * @param closed 关闭状态
         */
        public View { declaredFeatures = Set.copyOf(declaredFeatures); availableFeatures = Set.copyOf(availableFeatures); }
    }
}
