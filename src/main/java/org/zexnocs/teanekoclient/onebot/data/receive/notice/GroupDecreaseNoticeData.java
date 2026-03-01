package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 群成员减少事件数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class GroupDecreaseNoticeData {
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
     * 子类型。
     * 1. leave: 主动退群
     * 2. kick: 成员被踢
     */
    @JsonProperty("sub_type")
    private String subType;

    /**
     * 群号。
     */
    @JsonProperty("group_id")
    private long groupId;

    /**
     * 操作者 QQ 号。
     * 如果是主动退群，则为 0。
     */
    @JsonProperty("operator_id")
    private long operatorId;

    /**
     * 被操作者 QQ 号。
     */
    @JsonProperty("user_id")
    private long userId;

    /**
     * notice_type
     * 等于 "group_decrease"
     */
    @JsonProperty("notice_type")
    private String noticeType;

    /**
     * post_type
     * 等于 "notice"
     */
    @JsonProperty("post_type")
    private String postType;

    /**
     * 获取退群原因，转化成人类可读的字符串。
     * @return 退群原因。
     */
    public String getDecreaseReason() {
        return switch (subType) {
            case "leave" -> "主动退群";
            case "kick" -> "被踢出群";
            default -> "未知: " + subType;
        };
    }

    /**
     * 从 JSON 字符串中解析 GroupDecreaseNoticeData 对象。
     * @param json JSON 字符串。
     * @return GroupDecreaseNoticeData 对象。
     */
    public static GroupDecreaseNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, GroupDecreaseNoticeData.class);
    }
}
