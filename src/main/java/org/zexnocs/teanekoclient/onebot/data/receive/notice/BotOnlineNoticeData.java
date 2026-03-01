package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Bot 上线通知数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class BotOnlineNoticeData {
    @JsonProperty("post_type")
    private String postType;

    @JsonProperty("notice_type")
    private String noticeType;

    @JsonProperty("time")
    private long time;

    @JsonProperty("self_id")
    private long selfId;

    @JsonProperty("reason")
    private String reason;

    /**
     * 从 JSON 字符串中解析 BotOnlineNoticeData 对象。
     *
     * @param json   JSON 字符串。
     * @return BotOnlineNoticeData 对象。
     */
    public static BotOnlineNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, BotOnlineNoticeData.class);
    }
}
