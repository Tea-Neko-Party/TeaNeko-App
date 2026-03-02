package org.zexnocs.teanekoclient.onebot.core;

import lombok.Getter;
import org.springframework.context.annotation.Lazy;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;

/**
 * todo
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@TeaNekoClient(value = OnebotTeaNekoClient.ID, description = """
        Onebot 11 协议的客户端。""")
public class OnebotTeaNekoClient implements ITeaNekoClient {
    public static final String ID = "onebot";

    @Getter
    private final OnebotClient client;

    @Getter
    private final OnebotToolbox teaNekoToolbox;

    @Lazy
    public OnebotTeaNekoClient(OnebotClient onebotClient, OnebotToolbox onebotToolbox) {
        this.client = onebotClient;
        this.teaNekoToolbox = onebotToolbox;
    }

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
