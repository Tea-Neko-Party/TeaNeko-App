package org.zexnocs.teanekoagent.client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
/**
 * 保留 teaneko-agent 客户端 ID，仅在本进程执行结构化操作。
 * @param getClient 内部执行客户端
 * @param getTeaNekoToolbox 专用工具箱
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.5.0-l
 */
@TeaNekoClient(value = TeaNekoAgentClient.ID, description = "TeaNeko App 内置 Agent 客户端")
public record TeaNekoAgentClient(TeaNekoAgentInternalClient getClient, TeaNekoAgentToolbox getTeaNekoToolbox) implements ITeaNekoClient {
    public static final String ID = "teaneko-agent";
    /**
     * 注入唯一新版实现，不建立连接或触发模型。
     * @param getClient 内部执行器
     * @param getTeaNekoToolbox 专用工具箱
     */
    @Lazy @Autowired public TeaNekoAgentClient { }
    /**
     * 保持现有客户端登记 ID。
     * @return teaneko-agent
     */
    @Override public String getClientId() { return ID; }
}
