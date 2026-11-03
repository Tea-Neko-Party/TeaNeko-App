package org.zexnocs.teanekoclient.onebot.core;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.console.ClientConsole;
import org.zexnocs.teanekoclient.onebot.data.response.params.OnebotMessageSendResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;
import org.zexnocs.teanekoclient.onebot.data.send.OnebotSendData;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.GroupMsgSendParamsData;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.PrivateMsgSendParamsData;
import org.zexnocs.teanekoclient.onebot.event.sent.OnebotMessageSentEvent;
import org.zexnocs.teanekoclient.onebot.state.OnebotState;
import org.zexnocs.teanekoclient.onebot.state.OnebotStateMachine;
import org.zexnocs.teanekoclient.onebot.utils.OnebotContentListBuilder;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * OneBot 指令台能力：要求真实唯一账号、原平台状态和固定连接，保留原发送事件及响应解析。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Service
public final class OnebotConsoleBridge {
    private final OnebotClient client;
    private final OnebotChatDirectory directory;
    private final OnebotStateMachine state;
    private final ISenderService sender;
    private final ObjectMapper mapper;

    /**
     * @param client 原真实传输
     * @param directory 原账号目录
     * @param state 原平台状态，不以管理身份绕过
     * @param sender 原发送事件与回执服务
     * @param mapper 原 OneBot 协议转换器
     */
    public OnebotConsoleBridge(OnebotClient client, OnebotChatDirectory directory, OnebotStateMachine state,
            ISenderService sender, @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        this.client = client;
        this.directory = directory;
        this.state = state;
        this.sender = sender;
        this.mapper = mapper;
    }

    /**
     * @param target 原服务端解析的已知目标
     * @return 当前事实，不把连接数当作账号就绪
     */
    public CompletionStage<ClientConsole.State> describe(ChatTargets.Resolved target) {
        return directory.accounts().thenApply(accounts -> {
            var identity = client.connectionIdentity();
            String account = accounts.stream().filter(value -> value.ref().equals(target.accountRef()))
                    .map(ChatTargets.Account::platformId).findFirst().orElse("");
            String version = Long.toString(identity.epoch());
            if (identity.connections() == 0) {
                return new ClientConsole.State("DISCONNECTED", version, "OneBot 当前没有连接。");
            }
            if (identity.connections() != 1) {
                return new ClientConsole.State("CONFLICT", version, "存在多个连接，尚未支持定向多账号执行。");
            }
            if (!identity.ready()) {
                return new ClientConsole.State("IDENTITY_PENDING", version, "连接已建立，当前账号身份尚未确认。");
            }
            if (account.isBlank() || !account.equals(identity.selfId())) {
                return new ClientConsole.State("ACCOUNT_MISMATCH", version, "所选账号不是当前唯一连接的已确认账号。");
            }
            if (state.getCurrentState() != OnebotState.DEFAULT) {
                return new ClientConsole.State("PLATFORM_RESTRICTED", version, "原 OneBot 状态限制当前指令操作；不会借管理身份绕过。");
            }
            return new ClientConsole.State("READY", version, "账号与唯一连接已确认，实际发送前仍会复核。");
        });
    }

    /**
     * @param target 原固定账号及聊天
     * @param authorization 当前会话及平台插件检查
     * @return 固定本次连接，实际发送不回退到其他会话
     */
    public CompletionStage<ClientConsole.Lease> capture(ChatTargets.Resolved target, Runnable authorization) {
        return describe(target).thenApply(status -> {
            if (!status.state().equals("READY")) {
                throw new IllegalStateException("OneBot 当前账号或连接不可执行指令。");
            }
            var expected = client.connectionIdentity();
            if (!status.connection().equals(Long.toString(expected.epoch()))) {
                throw new IllegalStateException("OneBot 连接已经换代，请重新读取目标。");
            }
            Runnable checked = () -> {
                authorization.run();
                if (!expected.ready() || !expected.equals(client.connectionIdentity()) || state.getCurrentState() != OnebotState.DEFAULT) {
                    throw new IllegalStateException("OneBot 原连接或运行状态已失效，不会自动换连接。");
                }
            };
            checked.run();
            return new ClientConsole.Lease(OnebotTeaNekoClient.class, target, status.connection(), checked,
                    text -> send(target, expected, checked, text));
        });
    }

    /**
     * @param target 原固定目标
     * @param identity 原唯一连接
     * @param guard 实际写入前检查
     * @param text 明确文本
     * @return 实际平台回执的消息号
     */
    private CompletionStage<String> send(ChatTargets.Resolved target, OnebotClient.ConnectionIdentity identity,
            Runnable guard, String text) {
        guard.run();
        var contents = OnebotContentListBuilder.builder().addText(text).build();
        if (target.kind().equals("GROUP")) {
            return send(GroupMsgSendParamsData.builder().groupId(Long.parseLong(target.platformId())).messageList(contents).build(), identity, guard);
        }
        return send(PrivateMsgSendParamsData.builder().userId(Long.parseLong(target.platformId())).messageList(contents).build(), identity, guard);
    }

    /**
     * @param params 原类型化 OneBot 消息参数
     * @param identity 固定传输身份
     * @param guard 当前来源守卫
     * @param <S> 原消息协议参数类型
     * @return 单一真实回执，不导出协议对象
     */
    private <S extends ISendParamsData<OnebotMessageSendResponseData>> CompletionStage<String> send(
            S params, OnebotClient.ConnectionIdentity identity, Runnable guard) {
        var pinned = new IClient() {
            /** @param message 原发送事件冻结后的协议正文 */
            @Override
            public void send(String message) {
                client.sendPinned(identity, message, guard);
            }

            /**
             * @param message 不接受回流的原始正文
             * @return 本适配器无入站能力
             * @throws UnsupportedOperationException 当试图把管理传输当作原生入站时
             */
            @Override
            public IEvent<?> handle(String message) {
                throw new UnsupportedOperationException("指令台固定发送能力不能伪造原生入站。");
            }
        };
        var data = new OnebotSendData<>(params, pinned, mapper);
        var event = new OnebotMessageSentEvent<>("console", data, null);
        return TaskStages.successful(sender.send(event, Duration.ZERO, 0, Duration.ZERO), "指令台平台发送")
                .thenApply(responses -> {
                    // 原响应只作为已发生事实，撤权后仍交给宿主记录；此处不发起新的副作用。
                    if (responses == null || responses.size() != 1 || responses.getFirst() == null
                            || responses.getFirst().getMessageId() == null || responses.getFirst().getMessageId().isBlank()) {
                        throw new IllegalStateException("OneBot 没有返回唯一有效消息标识，实际发送未确认。");
                    }
                    return responses.getFirst().getMessageId();
                });
    }
}
