package org.zexnocs.teanekoapp.client.api;

import org.zexnocs.teanekoapp.sender.api.ITeaNekoToolbox;

/**
 * Tea Neko 客户端
 * 用于发送给 Tea Neko 服务器消息并获取新消息。
 * 一般作为适配器的基础接口，需要实现接口来与 Tea Neko 服务器进行通信。
 *
 * @author zExNocs
 * @date 2026/02/21
 * @since 4.0.8
 */
public interface ITeaNekoClient {
    /**
     * 获取 Client ID，用于标识客户端，也用作 群组 Command Scope 的作用域 ID 前缀。
     * <p>Client ID 改变会导致之前 {@link org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager} 群组相关设置失效。
     *
     * @return Client ID
     */
    String getClientId();

    /**
     * 获取原客户端，可用于发送消息
     *
     * @return {@link IClient} 原客户端
     */
    IClient getClient();

    /**
     * 获取 Tea Neko 发送器工具箱
     *
     * @return {@link ITeaNekoToolbox} Tea Neko 发送器工具箱
     */
    ITeaNekoToolbox getTeaNekoToolbox();
}
