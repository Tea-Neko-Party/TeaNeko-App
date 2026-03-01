package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 群消息撤回通知数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class GroupRecallNoticeData {
    @JsonProperty("group_id")
    private long groupId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("message_id")
    private long messageId;

    @JsonProperty("operator_id")
    private long operatorId;

    @JsonProperty("tip")
    private String tip;

    @JsonProperty("time")
    private long time;

    @JsonProperty("notice_type")
    private String noticeType;

    @JsonProperty("self_id")
    private long selfId;

    @JsonProperty("post_type")
    private String postType;

    /**
     * 从 JSON 字符串中解析 GroupRecallNoticeData 对象。
     * @param json JSON 字符串。
     * @return GroupRecallNoticeData 对象。
     */
    public static GroupRecallNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, GroupRecallNoticeData.class);
    }
}
