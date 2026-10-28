package org.zexnocs.teanekoapp.fake_client;

import lombok.Getter;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;

/**
 * 用于隔离验证场景的FakeTeaNekoClient测试数据或夹具。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.2.3
 * @version 4.5.0-l
 */
@TeaNekoClient(
        value = FakeTeaNekoClient.ID,
        description = "测试专用客户端"
)
public class FakeTeaNekoClient implements ITeaNekoClient {
    public final static String ID = "fake-client";

    /// 原客户端
    @Getter
    private final FakeClient client;

    /// Tea Neko 发送器工具箱
    @Getter
    private final FakeTeaNekoToolbox teaNekoToolbox;

    /**
     * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
     *
     * @param client 用于本次测试的 {@code client} 数据
     * @param teaNekoToolbox 用于本次测试的 {@code teaNekoToolbox} 数据
     */
    public FakeTeaNekoClient(FakeClient client, FakeTeaNekoToolbox teaNekoToolbox) {
        this.client = client;
        this.teaNekoToolbox = teaNekoToolbox;
        this.client.bind(this);
    }

    /**
     * 获取 Client ID，用于标识客户端，也用作 群组 Command Scope 的作用域 ID 前缀。
     * <p>Client ID 改变会导致之前 {@link ICommandScopeManager} 群组相关设置失效。
     *
     * @return 测试客户端沿原集成链路使用的固定标识
     */
    @Override
    public String getClientId() {
        return ID;
    }
}
