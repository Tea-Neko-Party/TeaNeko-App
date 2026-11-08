package org.zexnocs.teanekoplugin.onebot.common.meow;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.event.notice.NotifyNoticeReceiveEvent;
import org.zexnocs.teanekoclient.onebot.sender.message.GroupMessageSender;
import org.zexnocs.teanekoclient.onebot.sender.message.PrivateMessageSender;
import org.zexnocs.teanekoclient.onebot.state.OnebotStateMachine;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 喵呜戳一戳服务，监听戳一戳事件，当被戳到时回复 "喵呜~"
 * 只有 onebot 协议的戳一戳事件会被监听到，其他协议的戳一戳事件将被忽略。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：戳一戳回复登记完整平台阶段，随事件参与插件排空。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 * @version 4.9.0-l
 */
@EventListener
@RequiredArgsConstructor
@PluginOwned("onebot.common")
public class MeowPokeService {

    private final PrivateMessageSender privateMessageSender;
    private final GroupMessageSender groupMessageSender;
    private final OnebotStateMachine onebotStateMachine;
    private final PluginAccess plugins;

    /**
     * 在当前平台状态允许时回复，资格和完成范围沿用原事件。
     * @param event 原通知事件
     */
    @EventHandler
    public void handle(NotifyNoticeReceiveEvent event) {
        var token = plugins.captureOwner("onebot.common");
        if(doNotHandle()) {
            return;
        }
        var data = event.getData();
        if (data.getTargetID() == data.getSelfID()) {
            plugins.check(token);
            if(data.getGroupID() != 0) {
                event.trackProcessing(groupMessageSender.getBuilder(AbstractEvent.getTokenForSender(),
                                String.valueOf(data.getGroupID()))
                        .addTextMessage("喵呜~").sendAsync());
            } else {
                event.trackProcessing(privateMessageSender.getBuilder(AbstractEvent.getTokenForSender(),
                                String.valueOf(data.getUserID()))
                        .addTextMessage("喵呜~").sendAsync());
            }
        }
    }

    /**
     * 是否要处理
     * @return 平台不在默认工作状态时为真
     */
    private boolean doNotHandle() {
        return switch (onebotStateMachine.getCurrentState()) {
            case DEFAULT -> false;
            default -> true;
        };
    }
}
