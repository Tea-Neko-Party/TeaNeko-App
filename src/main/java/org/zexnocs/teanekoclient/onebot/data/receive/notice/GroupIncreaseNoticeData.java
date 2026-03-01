package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 群成员增加事件数据。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class GroupIncreaseNoticeData {
    /**
     * 消息接收的时间。
     */
    @JsonProperty("time")
    private long time;

    /**
     * 机器人 QQ 号
     */
    @JsonProperty("self_id")
    private long selfId;

    /**
     * 等于 "notice"
     */
    @JsonProperty("post_type")
    private String postType;

    /**
     * 等于 "group_increase"
     */
    @JsonProperty("notice_type")
    private String noticeType;

    /**
     * 处理进群消息的管理员 QQ 号
     */
    @JsonProperty("operator_id")
    private long operatorId;

    /**
     * 子类型。
     * 1. approve：同意入群
     */
    @JsonProperty("sub_type")
    private String subType;

    /**
     * 群号。
     */
    @JsonProperty("group_id")
    private long groupId;

    /**
     * 加入者 QQ 号
     */
    @JsonProperty("user_id")
    private long userId;

    /**
     * 从 JSON 字符串中解析 GroupIncreaseNoticeData 对象。
     * @param json JSON 字符串。
     * @return GroupIncreaseNoticeData 对象。
     */
    public static GroupIncreaseNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, GroupIncreaseNoticeData.class);
    }
}
