package org.zexnocs.teanekoagent_old.sender;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.sender.SentEvent;
import org.zexnocs.teanekoapp.sender.api.ISendData;

/**
 * Agent 内置客户端 sender 事件。
 *
 * @param <S> Agent 发送数据类型
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public class AgentSentEvent<S extends ISendData<?>> extends SentEvent<S> {
    /**
     * 创建 Agent sender 事件。
     *
     * @param data Agent 发送数据
     */
    public AgentSentEvent(@Nullable S data) {
        super(data);
    }
}
