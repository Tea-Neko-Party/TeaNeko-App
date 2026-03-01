package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 群管理员变动通知数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class GroupAdminNoticeData {
    // 为 notice
    @JsonProperty("post_type")
    private String postType;

    // 为 group_admin
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

    // 群管理员 QQ 号
    @JsonProperty("user_id")
    private long userId;

    /**
     * 从 JSON 字符串中解析 GroupAdminNoticeData 对象。
     * @param json JSON 字符串。
     * @return GroupAdminNoticeData 对象。
     */
    public static GroupAdminNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, GroupAdminNoticeData.class);
    }
}
