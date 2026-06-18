package org.zexnocs.teanekoagent_old.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;

/**
 * TeaNeko Agent 内置客户端。
 * <br>该客户端运行在 App 进程内，不建立 WebSocket 连接。
 *
 * @param getClient 内置 Agent 原始客户端
 * @param getTeaNekoToolbox Agent 专属工具箱
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@TeaNekoClient(
        value = TeaNekoAgentClient.ID,
        description = "TeaNeko App 内置 Agent 客户端"
)
public record TeaNekoAgentClient(
        TeaNekoAgentInternalClient getClient,
        TeaNekoAgentToolbox getTeaNekoToolbox
) implements ITeaNekoClient {
    /** 客户端唯一 ID。 */
    public static final String ID = "teaneko-agent";

    /** 创建并注入内置 Agent 客户端。 */
    @Lazy
    @Autowired
    public TeaNekoAgentClient {
    }

    /** @return 客户端唯一 ID */
    @Override
    public String getClientId() {
        return ID;
    }
}
