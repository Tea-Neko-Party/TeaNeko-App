package org.zexnocs.teanekoagent_old.response;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.response.ResponseEvent;

/**
 * TeaNeko Agent 内置客户端响应事件。
 * <br>该客户端不会产生平台消息事件，所有 sender 结果均通过该事件返回。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public class AgentResponseEvent extends ResponseEvent {
    /**
     * 创建 Agent 响应事件。
     *
     * @param data Agent 响应数据
     */
    public AgentResponseEvent(@Nullable AgentResponseData data) {
        super(data, AgentResponseData.class);
    }
}
