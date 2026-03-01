package org.zexnocs.teanekoclient.onebot.data.receive.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 心跳事件数据类，用于接收 OneBot 心跳事件的数据。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatMetaEventData {
    @JsonProperty("interval")
    private long interval;

    @JsonProperty("status")
    private HeartbeatStatus status;

    @JsonProperty("meta_event_type")
    private String metaEventType;

    @JsonProperty("time")
    private long time;

    @JsonProperty("self_id")
    private long selfId;

    @JsonProperty("post_type")
    private String postType;


    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeartbeatStatus {
        @JsonProperty("app_initialized")
        private boolean appInitialized;

        @JsonProperty("app_enabled")
        private boolean appEnabled;

        @JsonProperty("app_good")
        private boolean appGood;

        @JsonProperty("online")
        private boolean online;

        @JsonProperty("good")
        private boolean good;
    }

    /**
     * 从 JSON 字符串中解析 HeartbeatMetaEventData 对象。
     *
     * @param json HeartbeatMetaEventData 对象的 JSON 字符串
     * @return HeartbeatMetaEventData 对象
     */
    public static HeartbeatMetaEventData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, HeartbeatMetaEventData.class);
    }
}
