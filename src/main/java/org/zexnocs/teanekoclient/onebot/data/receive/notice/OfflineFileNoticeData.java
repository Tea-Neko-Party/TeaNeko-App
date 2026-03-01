package org.zexnocs.teanekoclient.onebot.data.receive.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 离线文件通知数据类。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OfflineFileNoticeData {
    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("file")
    private Map<String, String> file;

    @JsonProperty("notice_type")
    private String noticeType;

    @JsonProperty("time")
    private long time;

    @JsonProperty("self_id")
    private long selfId;

    @JsonProperty("post_type")
    private String postType;

    /**
     * 从 JSON 字符串中解析 OfflineFileNoticeData 对象。
     * @param json JSON 字符串。
     * @return OfflineFileNoticeData 对象。
     */
    public static OfflineFileNoticeData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, OfflineFileNoticeData.class);
    }
}
