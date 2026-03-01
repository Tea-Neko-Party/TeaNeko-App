package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 群加精通知数据
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class EssenceNoticeData {
    // 为 notice
    @JsonProperty("post_type")
    private String postType;

    // 为 essence
    @JsonProperty("notice_type")
    private String noticeType;

    // 发送的时间戳
    @JsonProperty("time")
    private long time;

    // 机器人的 QQ 号
    @JsonProperty("self_id")
    private long selfId;

    // add | delete
    @JsonProperty("sub_type")
    private String subType;

    // 群号
    @JsonProperty("group_id")
    private long groupId;

    // 加精的消息 ID
    @JsonProperty("message_id")
    private long messageId;

    // 发送该加精消息的用户 QQ 号
    @JsonProperty("sender_id")
    private long senderId;

    // 加精的管理员 QQ 号
    @JsonProperty("operator_id")
    private long operatorId;

    /**
     * 从 JSON 字符串中解析 EssenceNoticeData 对象。
     * @param json JSON 字符串。
     * @return EssenceNoticeData 对象。
     */
    public static EssenceNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, EssenceNoticeData.class);
    }
}
