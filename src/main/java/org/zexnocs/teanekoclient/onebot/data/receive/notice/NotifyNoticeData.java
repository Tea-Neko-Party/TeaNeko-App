package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 通知事件数据。
 * 比如说戳一戳。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class NotifyNoticeData {
    /**
     * 机器人的 QQ 号。
     */
    @JsonProperty("self_id")
    private long selfID;

    /**
     * 被通知的 QQ 号。
     */
    @JsonProperty("target_id")
    private long targetID;

    /**
     * 通知者的 QQ 号。
     */
    @JsonProperty("user_id")
    private long userID;

    /**
     * 私聊的对方 QQ 号。
     * 如果是群聊的通知事件，则为 0。
     */
    @JsonProperty("sender_id")
    private long senderID;

    /**
     * 群聊的群号。
     * 如果是私有的通知事件，则为 0。
     */
    @JsonProperty("group_id")
    private long groupID = 0;

    /**
     * 事件的发生时间。
     */
    @JsonProperty("time")
    private long time;

    /**
     * post类型，等于 "notice"。
     */
    @JsonProperty("post_type")
    private String postType;

    /**
     * notice类型，等于 "notify"。
     */
    @JsonProperty("notice_type")
    private String noticeType;

    /**
     * 子类型。
     * 1. poke：戳一戳
     */
    @JsonProperty("sub_type")
    private String subType;

    // ---- lagrange ----
    /**
     * 行为，例如 "戳一戳"
     */
    @JsonProperty("action")
    private String action;

    /**
     * 后缀。
     */
    @JsonProperty("suffix")
    private String suffix;

    /**
     * 戳一戳的图片。
     */
    @JsonProperty("action_img_url")
    private String actionImageUrl;

    // ---- LLOneBot ----
    /**
     * LLOneBot 解析原始信息
     * 通知事件的原始信息。
     * 一般构成：
     * 0. qq：事件的发起者的信息。
     * 1. img：戳一戳的图片。
     * 2. nor：戳一戳的文本。例如 “戳了戳” “抱了抱”
     * 3. qq：事件接收者的信息。
     * 4. nor：戳一戳的结尾文本。
     */
    @JsonProperty("raw_info")
    private List<RawInfoClass> rawInfo;

    /**
     * 通知事件的原始信息。
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RawInfoClass {
        /**
         * 类型。
         * 1. qq: 具有 "col"、"nm"、"uid" 。可能有 "tp"。
         * 2. img: 图片，具有 "jp"、"src"。
         * 3. nor: 普通文本，具有 "txt"。
         */
        @JsonProperty("type")
        private String type;

        // 应该是行数。可以为 null。
        @JsonProperty("col")
        private String col = null;

        // 不知道是什么。可以为空字符或者 null。
        @JsonProperty("nm")
        private String nm = null;

        // uid。可以为 null。
        @JsonProperty("uid")
        private String uid = null;

        // jp。qq会员？可以为 null。
        @JsonProperty("jp")
        private String jp = null;

        // src。图片链接。可以为 null。
        @JsonProperty("src")
        private String src = null;

        // 戳一戳的文本。可以为 null。
        @JsonProperty("txt")
        private String txt = null;

        // tp。不知道是什么。
        @JsonProperty("tp")
        private String tp = null;
    }

    /**
     * 从 JSON 字符串中解析 NotifyNoticeData 对象。
     * @param json JSON 字符串。
     * @return NotifyNoticeData 对象。
     */
    public static NotifyNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, NotifyNoticeData.class);
    }
}
