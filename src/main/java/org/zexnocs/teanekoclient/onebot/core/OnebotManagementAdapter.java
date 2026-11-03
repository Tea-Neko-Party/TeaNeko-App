package org.zexnocs.teanekoclient.onebot.core;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;
import org.zexnocs.teanekoapp.web.client.TeaNekoClientManagement;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 将 OneBot 真实传输连接投影为平台状态，不把旧机器人 ID 当作定向执行许可。
 * <br>2026/11/03：贡献真实账号与已知聊天，支持离线设置和受限联系人同步。
 * <br>2026/11/03：指令台使用原账号确认及固定连接能力，状态限制继续生效。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
@TeaNekoClientManagement(OnebotTeaNekoClient.ID)
public final class OnebotManagementAdapter implements IClientManagementAdapter {
    private @org.jspecify.annotations.Nullable OnebotConsoleBridge console;

    /**
     * 2026/11/03：独立装配指令台能力，保留旧只读目录构造。
     * @param console 原平台固定连接桥接
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installConsole(OnebotConsoleBridge console) {
        this.console = console;
    }

    /**
     * @param target 原账号下真实目标
     * @return 当前状态，不把历史账号当作在线许可
     */
    @Override
    public CompletionStage<org.zexnocs.teanekoapp.web.console.ClientConsole.State> consoleState(ChatTargets.Resolved target) {
        return console == null ? IClientManagementAdapter.super.consoleState(target) : console.describe(target);
    }

    /**
     * @param target 原目标
     * @param guard 当前会话及平台插件
     * @return 固定原连接的能力
     */
    @Override
    public CompletionStage<org.zexnocs.teanekoapp.web.console.ClientConsole.Lease> consoleLease(ChatTargets.Resolved target, Runnable guard) {
        return console == null ? IClientManagementAdapter.super.consoleLease(target, guard) : console.capture(target, guard);
    }
    private final TeaNekoClientScanner clients;
    private final ObjectProvider<OnebotClient> transport;
    private final OnebotChatDirectory directory;

    /**
     * @param clients 实际启用的客户端目录
     * @param transport 延迟读取真实传输，不在扫描阶段重建客户端
     * @param directory 独立目录用例
     */
    public OnebotManagementAdapter(TeaNekoClientScanner clients, ObjectProvider<OnebotClient> transport,
                                   OnebotChatDirectory directory) {
        this.clients = clients;
        this.transport = transport;
        this.directory = directory;
    }

    /** @return 当前注册及连接事实，不暴露 session 或消息 */
    @Override
    public View describe() {
        if (clients.getPair(OnebotTeaNekoClient.ID) == null) {
            return new View(OnebotTeaNekoClient.ID, "OneBot", "DISABLED", 0,
                    "平台尚未启用或正在加载。", Instant.now(), true);
        }
        var client = transport.getIfAvailable();
        int count = client == null ? 0 : client.getOpenConnectionCount();
        return new View(OnebotTeaNekoClient.ID, "OneBot", count == 0 ? "DISCONNECTED" : "CONNECTED", count,
                count == 0 ? "当前没有连接，连接后才能使用平台操作。"
                        : "传输已连接；指令台还需确认唯一账号、目标及平台状态。", Instant.now(), true);
    }

    /** @return 本平台已知账号 */
    @Override
    public CompletionStage<List<ChatTargets.Account>> accounts() {
        return directory.accounts();
    }

    /**
     * @param account 原账号
     * @param after 原游标
     * @param kind 过滤类型
     * @return 有界聊天目录
     */
    @Override
    public CompletionStage<ChatTargets.Page> targets(String account, String after, String kind) {
        return directory.page(account, after, kind);
    }

    /**
     * @param account 原账号
     * @param target 原聊天
     * @return 当前真实定位
     */
    @Override
    public CompletionStage<ChatTargets.Resolved> resolve(String account, String target) {
        return directory.resolve(account, target);
    }

    /**
     * @param guard 当前授权
     * @return 实际更新的账号目录
     */
    @Override
    public CompletionStage<List<ChatTargets.Account>> discover(Runnable guard) {
        return directory.discover(guard);
    }
}
