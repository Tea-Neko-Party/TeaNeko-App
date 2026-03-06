package org.zexnocs.teanekoclient.onebot.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;

/**
 * Onebot TeaNeko 客户端类，封装了 OnebotClient 和 OnebotToolbox。
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@TeaNekoClient(value = OnebotTeaNekoClient.ID, description = """
        Onebot 11 协议的客户端。""")
public record OnebotTeaNekoClient(OnebotClient getClient, OnebotToolbox getTeaNekoToolbox) implements ITeaNekoClient {
    public static final String ID = "onebot";

    @Lazy
    @Autowired
    public OnebotTeaNekoClient {}

    /**
     * 获取 Client ID，用于标识客户端，也用作 群组 Command Scope 的作用域 ID 前缀。
     * <p>Client ID 改变会导致之前 {@link ICommandScopeManager} 群组相关设置失效。
     *
     * @return Client ID
     */
    @Override
    public String getClientId() {
        return ID;
    }
}
