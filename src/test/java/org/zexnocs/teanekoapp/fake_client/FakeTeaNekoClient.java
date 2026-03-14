package org.zexnocs.teanekoapp.fake_client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;

/**
 *
 *
 * @author zExNocs
 * @date 2026/03/14
 * @since 4.2.3
 */
@TeaNekoClient(
        value = FakeTeaNekoClient.ID,
        description = "测试专用客户端"
)
@RequiredArgsConstructor
public class FakeTeaNekoClient implements ITeaNekoClient {
    public final static String ID = "fake-client";

    /// 原客户端
    @Getter
    private final FakeClient client;

    /// Tea Neko 发送器工具箱
    @Getter
    private final FakeTeaNekoToolbox teaNekoToolbox;

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
