package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 群名片变更通知数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class GroupCardNoticeData {
    @JsonProperty("time")
    private long time;

    @JsonProperty("self_id")
    private long selfId;

    @JsonProperty("post_type")
    private String postType;

    @JsonProperty("group_id")
    private long groupId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("notice_type")
    private String noticeType;

    @JsonProperty("card_new")
    private String cardNew;

    @JsonProperty("card_old")
    private String cardOld;

    /**
     * 从 JSON 字符串中解析 GroupCardNoticeData 对象。
     * @param json JSON 字符串。
     * @return GroupCardNoticeData 对象。
     */
    public static GroupCardNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, GroupCardNoticeData.class);
    }
}
