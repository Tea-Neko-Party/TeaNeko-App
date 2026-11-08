package org.zexnocs.teanekoagent.integration.web;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.monitor.AgentActivityTimeline;
import org.zexnocs.teanekoapp.web.client.ManagementTargets;
import org.zexnocs.teanekocore.plugin.management.IManagementResource;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Agent 运行活动时间线，框架观测不依赖具体业务插件。
 * <br>2026/11/07：保留原管理资源键，将框架只读入口与认知业务拆开。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class AgentActivityResource implements IManagementResource<AgentActivityResource.Query, AgentActivityTimeline.Page> {
    private final AgentActivityTimeline timeline;

    /** @param timeline 当前宿主的活动窗口 */
    public AgentActivityResource(AgentActivityTimeline timeline) {
        this.timeline = timeline;
    }

    /** @return 保持兼容的框架只读资源 */
    @Override public Descriptor descriptor() {
        return new Descriptor("teaneko.agent", "activity", 1, "agent.console.activity", false, List.of());
    }

    /** @return 明确的有界分页请求 */
    @Override public Class<Query> requestType() {
        return Query.class;
    }

    /**
     * @param context 宿主已授权的真实聊天
     * @param request 分页游标与条数
     * @return 当前聊天的活动页面
     */
    @Override public CompletionStage<AgentActivityTimeline.Page> execute(ManagementContext context, Query request) {
        context.checkActive();
        var target = ManagementTargets.decode(context.scopeRef());
        return CompletableFuture.completedStage(timeline.page(new AgentScope(target.client(), target.scope()),
                request.after(), request.limit()));
    }

    /**
     * 活动窗口分页参数。
     * @param after 已读取的序号
     * @param limit 每页条数
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Query(long after, int limit) {
        /**
         * 拒绝无效或无界的活动查询。
         * @param after 原序号
         * @param limit 每页条数
         */
        public Query {
            if (after < 0 || limit < 1 || limit > 100) {
                throw new IllegalArgumentException("活动分页超出范围。");
            }
        }
    }
}
