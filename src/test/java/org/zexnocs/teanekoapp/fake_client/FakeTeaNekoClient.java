package org.zexnocs.teanekoapp.fake_client;

import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekoapp.sender.api.ITeaNekoToolbox;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;

/**
 * 一个用于测试的假 TeaNekoClient 实现。
 *
 * @author zExNocs
 * @date 2026/02/27
 */
@TeaNekoClient(FakeTeaNekoClient.ID)
public class FakeTeaNekoClient implements ITeaNekoClient {

    public final static String ID = "Fake Tea Neko Client";

    private final FakeClient fakeClient;

    public FakeTeaNekoClient(FakeClient fakeClient) {
        this.fakeClient = fakeClient;
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

    /**
     * 获取原客户端，可用于发送消息
     *
     * @return {@link IClient} 原客户端
     */
    @Override
    public IClient getClient() {
        return fakeClient;
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
