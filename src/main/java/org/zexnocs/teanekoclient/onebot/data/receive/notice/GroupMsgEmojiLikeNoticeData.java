package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 群消息表情点赞事件数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class GroupMsgEmojiLikeNoticeData {
    /// 发送的时间戳
    @JsonProperty("time")
    private long time;

    /// 机器人的 QQ 号
    @JsonProperty("self_id")
    private long selfId;

    /// 为 notice
    @JsonProperty("post_type")
    private String postType;

    /// 群号
    @JsonProperty("group_id")
    private long groupId;

    /// 发送者的 QQ 号
    @JsonProperty("user_id")
    private long userId;


    /// 为 group_msg_emoji_like
    @JsonProperty("notice_type")
    private String noticeType;

    /// 点 emoji 的消息 ID
    @JsonProperty("message_id")
    private long messageId;

    /// 是否是点赞
    @JsonProperty("is_add")
    private boolean isAdd;

    /// 点赞的表情列表
    @JsonProperty("likes")
    private List<LikeData> likes;

    public static class LikeData {
        /// 表情 ID
        @JsonProperty("emoji_id")
        private String EmojiId;

        /// 数量
        @JsonProperty("count")
        private int count;
    }

    /**
     * 从 JSON 字符串中解析 GroupMsgEmojiLikeNoticeData 对象。
     * @param json JSON 字符串。
     * @return GroupMsgEmojiLikeNoticeData 对象。
     */
    public static GroupMsgEmojiLikeNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, GroupMsgEmojiLikeNoticeData.class);
    }
}
