package org.zexnocs.teanekoagent.integration.web;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.plugin.management.GlobalManagementResource;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 独立空间管理使用实例入口；与聊天资源隔离，写入仍走原持久事务及操作账本。
 * <br>2026/11/06：独立目录只读持久元数据，不初始化空间或改变聊天绑定。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
public final class AgentLibraryResources {
    /** 禁止实例化资源容器。 */
    private AgentLibraryResources() {}

    /**
 * 独立memory-space资源；不接受隐式聊天选择。
 * <br>2026/11/06：独立目录只读持久元数据，不初始化空间或改变聊天绑定。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
    @Component
    public static final class Memory implements GlobalManagementResource<AgentConsole.MemoryQuery, Object> {
        private final AgentConsoleService service;
        /** @param service 原领域服务 */
        public Memory(AgentConsoleService service) { this.service = service; }
        /** @return 独立实例资源声明 */
        @Override public Descriptor descriptor() {
            return new Descriptor("teaneko.agent", "memory-space", 1, "agent.library.memory-space", true, List.of());
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
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
    @Component
    public static final class Personality implements GlobalManagementResource<AgentPersonalityLibraryService.Query, Object> {
        private final AgentPersonalityLibraryService service;
        /** @param service 原领域服务 */
        public Personality(AgentPersonalityLibraryService service) { this.service = service; }
        /** @return 独立实例资源声明 */
        @Override public Descriptor descriptor() {
            return new Descriptor("teaneko.agent", "personality-space", 1, "agent.library.personality-space", true, List.of());
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
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
    @Component
    public static final class Profiles implements GlobalManagementResource<AgentConsole.ProfileQuery, Object> {
        private final AgentConsoleService service;
        /** @param service 原领域服务 */
        public Profiles(AgentConsoleService service) { this.service = service; }
        /** @return 独立实例资源声明 */
        @Override public Descriptor descriptor() {
            return new Descriptor("teaneko.agent", "profile-space", 1, "agent.library.profile-space", false, List.of());
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
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
    @Component
    public static final class Layers implements GlobalManagementResource<AgentLayersConsole.Query, Object> {
        private final AgentLayersConsoleService service;
        /** @param service 原领域服务 */
        public Layers(AgentLayersConsoleService service) { this.service = service; }
        /** @return 独立实例资源声明 */
        @Override public Descriptor descriptor() {
            return new Descriptor("teaneko.agent", "layers-space", 1, "agent.library.layers-space", true, List.of());
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
