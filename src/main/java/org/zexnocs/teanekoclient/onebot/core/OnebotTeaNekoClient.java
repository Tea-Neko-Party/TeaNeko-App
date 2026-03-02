package org.zexnocs.teanekoclient.onebot.core;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.sender.api.ITeaNekoToolbox;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;

/**
 * todo
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Service
public class OnebotTeaNekoClient implements ITeaNekoClient {
    /**
     * 获取 Client ID，用于标识客户端，也用作 群组 Command Scope 的作用域 ID 前缀。
     * <p>Client ID 改变会导致之前 {@link ICommandScopeManager} 群组相关设置失效。
     *
     * @return Client ID
     */
    @Override
    public String getClientId() {
        return "";
    }

    /**
     * 获取原客户端，可用于发送消息
     *
     * @return {@link IClient} 原客户端
     */
    @Override
    public IClient getClient() {
        return null;
    }

    /**
     * 获取 Tea Neko 发送器工具箱
     *
     * @return {@link ITeaNekoToolbox} Tea Neko 发送器工具箱
     */
    @Override
    public ITeaNekoToolbox getTeaNekoToolbox() {
        return null;
    }
}
