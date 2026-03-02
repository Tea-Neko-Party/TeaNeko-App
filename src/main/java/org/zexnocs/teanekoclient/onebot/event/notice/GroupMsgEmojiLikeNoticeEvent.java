package org.zexnocs.teanekoclient.onebot.event.notice;

import org.zexnocs.teanekoclient.onebot.data.receive.notice.GroupMsgEmojiLikeNoticeData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;

/**
 * 群消息表情点赞通知事件
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(GroupMsgEmojiLikeNoticeEvent.KEY)
public class GroupMsgEmojiLikeNoticeEvent extends AbstractEvent<GroupMsgEmojiLikeNoticeData> {
    public static final String KEY = NoticeReceiveEvent.PARSE_SUFFIX_KEY + "group_msg_emoji_like";

    public GroupMsgEmojiLikeNoticeEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(GroupMsgEmojiLikeNoticeData.fromJson(information, eventShareComponent.objectMapper), GroupMsgEmojiLikeNoticeData.class);
    }
}