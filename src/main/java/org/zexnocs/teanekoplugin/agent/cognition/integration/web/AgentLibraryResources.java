package org.zexnocs.teanekoplugin.agent.cognition.integration.web;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.plugin.management.IGlobalManagementResource;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 独立空间管理使用实例入口；与聊天资源隔离，写入仍走原持久事务及操作账本。
 * <br>2026/11/06：独立目录只读持久元数据，不初始化空间或改变聊天绑定。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class AgentLibraryResources {
    /** 禁止实例化资源容器。 */
    private AgentLibraryResources() {}

    /**
     * 独立memory-space资源；不接受隐式聊天选择。
     * <br>2026/11/06：独立目录只读持久元数据，不初始化空间或改变聊天绑定。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.9.0-l
     */
    @Component
    public static final class Memory implements IGlobalManagementResource<AgentConsole.MemoryQuery, Object> {
        private final AgentConsoleService service;
        /** @param service 原领域服务 */
        public Memory(AgentConsoleService service) { this.service = service; }
        /** @return 独立实例资源声明 */
        @Override public Descriptor descriptor() {
            return new Descriptor("agent.cognition", "memory-space", 1, "agent.library.memory-space", true, List.of());
        }
        /** @return 明确参数类型 */
        @Override public Class<AgentConsole.MemoryQuery> requestType() { return AgentConsole.MemoryQuery.class; }
        /**
         * @param context 原实例管理能力
         * @param request 原版本和操作编号
         * @return 原领域结果
         */
        @Override public CompletionStage<Object> execute(ManagementContext context, AgentConsole.MemoryQuery request) {
            context.checkActive();
            if (!AgentConsoleService.global(context) || request.namespace().isBlank() && !request.action().equals("OPERATION")) {
                throw new IllegalArgumentException("请明确选择已有空间；聊天绑定及分区恢复请在对应聊天中操作。");
            }
            return service.memory(context, request);
        }
    }

    /**
     * 独立personality-space资源；不接受隐式聊天选择。
     * <br>2026/11/06：独立目录只读持久元数据，不初始化空间或改变聊天绑定。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.9.0-l
     */
    @Component
    public static final class Personality implements IGlobalManagementResource<AgentPersonalityLibraryService.Query, Object> {
        private final AgentPersonalityLibraryService service;
        /** @param service 原领域服务 */
        public Personality(AgentPersonalityLibraryService service) { this.service = service; }
        /** @return 独立实例资源声明 */
        @Override public Descriptor descriptor() {
            return new Descriptor("agent.cognition", "personality-space", 1, "agent.library.personality-space", true, List.of());
        }
        /** @return 明确参数类型 */
        @Override public Class<AgentPersonalityLibraryService.Query> requestType() { return AgentPersonalityLibraryService.Query.class; }
        /**
         * @param context 原实例管理能力
         * @param request 原版本和操作编号
         * @return 原领域结果
         */
        @Override public CompletionStage<Object> execute(ManagementContext context, AgentPersonalityLibraryService.Query request) {
            context.checkActive();
            if (!AgentConsoleService.global(context) || request.key().isBlank() || request.action().equals("BIND")) {
                throw new IllegalArgumentException("请明确选择已有空间；聊天绑定及分区恢复请在对应聊天中操作。");
            }
            return service.execute(context, request);
        }
    }

    /**
     * 独立profile-space资源；不接受隐式聊天选择。
     * <br>2026/11/06：独立目录只读持久元数据，不初始化空间或改变聊天绑定。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.9.0-l
     */
    @Component
    public static final class Profiles implements IGlobalManagementResource<AgentConsole.ProfileQuery, Object> {
        private final AgentConsoleService service;
        /** @param service 原领域服务 */
        public Profiles(AgentConsoleService service) { this.service = service; }
        /** @return 独立实例资源声明 */
        @Override public Descriptor descriptor() {
            return new Descriptor("agent.cognition", "profile-space", 1, "agent.library.profile-space", false, List.of());
        }
        /** @return 明确参数类型 */
        @Override public Class<AgentConsole.ProfileQuery> requestType() { return AgentConsole.ProfileQuery.class; }
        /**
         * @param context 原实例管理能力
         * @param request 原版本和操作编号
         * @return 原领域结果
         */
        @Override public CompletionStage<Object> execute(ManagementContext context, AgentConsole.ProfileQuery request) {
            context.checkActive();
            if (!AgentConsoleService.global(context) || request.namespace().isBlank()) {
                throw new IllegalArgumentException("请明确选择已有空间；聊天绑定及分区恢复请在对应聊天中操作。");
            }
            return service.profile(context, request);
        }
    }

    /**
     * 独立layers-space资源；不接受隐式聊天选择。
     * <br>2026/11/06：独立目录只读持久元数据，不初始化空间或改变聊天绑定。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.9.0-l
     */
    @Component
    public static final class Layers implements IGlobalManagementResource<AgentLayersConsole.Query, Object> {
        private final AgentLayersConsoleService service;
        /** @param service 原领域服务 */
        public Layers(AgentLayersConsoleService service) { this.service = service; }
        /** @return 独立实例资源声明 */
        @Override public Descriptor descriptor() {
            return new Descriptor("agent.cognition", "layers-space", 1, "agent.library.layers-space", true, List.of());
        }
        /** @return 明确参数类型 */
        @Override public Class<AgentLayersConsole.Query> requestType() { return AgentLayersConsole.Query.class; }
        /**
         * @param context 原实例管理能力
         * @param request 原版本和操作编号
         * @return 原领域结果
         */
        @Override public CompletionStage<Object> execute(ManagementContext context, AgentLayersConsole.Query request) {
            context.checkActive();
            if (!AgentConsoleService.global(context) || request.namespace().isBlank() || request.action().startsWith("REPLAY")) {
                throw new IllegalArgumentException("请明确选择已有空间；聊天绑定及分区恢复请在对应聊天中操作。");
            }
            return service.execute(context, request);
        }
    }
}
