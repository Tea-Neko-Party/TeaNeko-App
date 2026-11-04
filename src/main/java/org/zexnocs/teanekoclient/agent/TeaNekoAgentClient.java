package org.zexnocs.teanekoclient.agent;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
/**
 * 保留 teaneko-agent 客户端 ID，仅在本进程执行结构化操作。
 * <br>2026/11/02：惰性依赖改用接口及显式限定名，避免为 final 实现生成 CGLIB 代理。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param getClient 内部执行客户端
 * @param getTeaNekoToolbox 专用工具箱
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
@TeaNekoClient(value = TeaNekoAgentClient.ID, description = "TeaNeko App 内置 Agent 客户端")
// 2026/11/02：通过接口代理保留惰性装配和唯一客户端归属。
public record TeaNekoAgentClient(
        @org.springframework.beans.factory.annotation.Qualifier("teaNekoAgentInternalClient") org.zexnocs.teanekoapp.client.api.IClient getClient,
        @org.springframework.beans.factory.annotation.Qualifier("teaNekoAgentToolbox") org.zexnocs.teanekoclient.agent.tools.ITeaNekoAgentToolbox getTeaNekoToolbox) implements ITeaNekoClient {
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
