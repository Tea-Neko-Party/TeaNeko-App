package org.zexnocs.teanekoagent.integration.web;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.runtime.monitor.AgentActivityTimeline;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekocore.plugin.management.ManagementResource;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 在原管理网关贡献类型化资源，新增资源继续通过 Spring Bean 扩展。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.0-l
 */
public final class AgentConsoleResources {
    /**
     * 原持久化三层记忆及长期维护贡献。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    @Component
    public static final class Layers implements ManagementResource<AgentLayersConsole.Query, Object> {
        private final AgentLayersConsoleService service;
        /** @param service 原三层领域编排 */
        public Layers(AgentLayersConsoleService service) {
            this.service = service;
        }
        /** @return 明确的管理资源 */
        @Override
        public Descriptor descriptor() {
            return AgentConsoleResources.descriptor("layers", true);
        }
        /** @return 固定请求类型 */
        @Override
        public Class<AgentLayersConsole.Query> requestType() {
            return AgentLayersConsole.Query.class;
        }
        /**
         * @param context 原授权
         * @param request 固定读取或维护参数
         * @return 原持久化视图或回执
         */
        @Override
        public CompletionStage<Object> execute(ManagementContext context, AgentLayersConsole.Query request) {
            return service.execute(context, request);
        }
    }
    /** 禁止实例化贡献命名空间。 */
    private AgentConsoleResources() {}

    /**
     * @param id 稳定资源标识
     * @param mutation 是否可能修改
     * @return 原网关资源声明
     */
    private static ManagementResource.Descriptor descriptor(String id, boolean mutation) {
        return new ManagementResource.Descriptor("teaneko.agent", id, 1,
                "agent.console." + id, mutation, List.of());
    }

    /**
     * 活动时间线只读贡献。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    @Component
    public static final class Activity implements ManagementResource<AgentConsole.ActivityQuery, AgentActivityTimeline.Page> {
        private final AgentActivityTimeline timeline;
        /** @param timeline 实际宿主节点窗口 */
        public Activity(AgentActivityTimeline timeline) {
            this.timeline = timeline;
        }
        /** @return 只读资源声明 */
        @Override
        public Descriptor descriptor() {
            return AgentConsoleResources.descriptor("activity", false);
        }
        /** @return 固定请求类型 */
        @Override
        public Class<AgentConsole.ActivityQuery> requestType() {
            return AgentConsole.ActivityQuery.class;
        }
        /**
         * @param context 可信目标与授权
         * @param request 有界游标
         * @return 只包含当前聊天的节点
         */
        @Override
        public CompletionStage<AgentActivityTimeline.Page> execute(ManagementContext context, AgentConsole.ActivityQuery request) {
            context.checkActive();
            return CompletableFuture.completedStage(timeline.page(AgentConsoleService.scope(context), request.after(), request.limit()));
        }
    }

    /**
     * 记忆普通查看及明确管理动作贡献。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    @Component
    public static final class Memory implements ManagementResource<AgentConsole.MemoryQuery, Object> {
        private final AgentConsoleService service;
        /** @param service 原领域编排 */
        public Memory(AgentConsoleService service) {
            this.service = service;
        }
        /** @return 明确管理资源 */
        @Override
        public Descriptor descriptor() {
            return AgentConsoleResources.descriptor("memory", true);
        }
        /** @return 固定请求类型 */
        @Override
        public Class<AgentConsole.MemoryQuery> requestType() {
            return AgentConsole.MemoryQuery.class;
        }
        /**
         * @param context 原权限
         * @param request 明确动作
         * @return 原事务结果
         */
        @Override
        public CompletionStage<Object> execute(ManagementContext context, AgentConsole.MemoryQuery request) {
            return service.memory(context, request);
        }
    }

    /**
     * 证据画像只读贡献，修改继续走同一事实编辑。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    @Component
    public static final class Profiles implements ManagementResource<AgentConsole.ProfileQuery, Object> {
        private final AgentConsoleService service;
        /** @param service 原画像投影编排 */
        public Profiles(AgentConsoleService service) {
            this.service = service;
        }
        /** @return 只读资源声明 */
        @Override
        public Descriptor descriptor() {
            return AgentConsoleResources.descriptor("profiles", false);
        }
        /** @return 明确主体请求 */
        @Override
        public Class<AgentConsole.ProfileQuery> requestType() {
            return AgentConsole.ProfileQuery.class;
        }
        /**
         * @param context 原权限
         * @param request 原主体和代次
         * @return 有界画像证据
         */
        @Override
        public CompletionStage<Object> execute(ManagementContext context, AgentConsole.ProfileQuery request) {
            return service.profile(context, request);
        }
    }

    /**
     * 独立人格补正管理贡献。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    @Component
    public static final class Personality implements ManagementResource<AgentConsole.PersonalityQuery, Object> {
        private final AgentConsoleService service;
        /** @param service 原人格事务编排 */
        public Personality(AgentConsoleService service) {
            this.service = service;
        }
        /** @return 明确管理资源 */
        @Override
        public Descriptor descriptor() {
            return AgentConsoleResources.descriptor("personality", true);
        }
        /** @return 固定动作类型 */
        @Override
        public Class<AgentConsole.PersonalityQuery> requestType() {
            return AgentConsole.PersonalityQuery.class;
        }
        /**
         * @param context 原权限
         * @param request 原绑定与预期版本
         * @return 明确补正结果
         */
        @Override
        public CompletionStage<Object> execute(ManagementContext context, AgentConsole.PersonalityQuery request) {
            return service.personality(context, request);
        }
    }
}
