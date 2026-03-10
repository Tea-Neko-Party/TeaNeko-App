package org.zexnocs.teanekoplugin.onebot.meow;

import org.zexnocs.teanekoclient.onebot.event.notice.NotifyNoticeReceiveEvent;
import org.zexnocs.teanekoclient.onebot.sender.message.GroupMessageSender;
import org.zexnocs.teanekoclient.onebot.sender.message.PrivateMessageSender;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 喵呜戳一戳服务，监听戳一戳事件，当被戳到时回复 "喵呜~"
 * 只有 onebot 协议的戳一戳事件会被监听到，其他协议的戳一戳事件将被忽略。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@EventListener
public class MeowPokeService {

    private final PrivateMessageSender privateMessageSender;
    private final GroupMessageSender groupMessageSender;

    public MeowPokeService(PrivateMessageSender privateMessageSender, GroupMessageSender groupMessageSender) {
        this.privateMessageSender = privateMessageSender;
        this.groupMessageSender = groupMessageSender;
    }

    @EventHandler
    public void handle(NotifyNoticeReceiveEvent event) {
        var data = event.getData();
        if (data.getTargetID() == data.getSelfID()) {
            if(data.getGroupID() != 0) {
                groupMessageSender.getBuilder(String.valueOf(data.getGroupID()), AbstractEvent.getEventToken())
                        .sendTextMessage("喵呜~");
            } else {
                privateMessageSender.getBuilder(String.valueOf(data.getUserID()), AbstractEvent.getEventToken())
                        .sendTextMessage("喵呜~");
            }
        }
    }
}
