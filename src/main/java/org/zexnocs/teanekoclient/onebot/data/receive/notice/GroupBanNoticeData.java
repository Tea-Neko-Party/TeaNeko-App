package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 群禁言通知数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class GroupBanNoticeData {
    @JsonProperty("post_type")
    private String postType;

    @JsonProperty("notice_type")
    private String noticeType;

    @JsonProperty("group_id")
    private long groupId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("operator_id")
    private long operatorId;

    @JsonProperty("sub_type")
    private String subType;

    @JsonProperty("duration")
    private long duration;

    @JsonProperty("time")
    private long time;

    @JsonProperty("self_id")
    private long selfId;

    /**
     * 从 JSON 字符串中解析 GroupBanNoticeData 对象。
     * @param json JSON 字符串。
     * @return GroupBanNoticeData 对象。
     */
    public static GroupBanNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, GroupBanNoticeData.class);
    }
}
