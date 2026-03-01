package org.zexnocs.teanekoclient.onebot.data.receive.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 生命周期元事件数据类，包含了生命周期事件的相关信息。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LifecycleMetaEventData {
    @JsonProperty("sub_type")
    private String subType;

    @JsonProperty("time")
    private long time;

    @JsonProperty("meta_event_type")
    private String metaEventType;

    @JsonProperty("self_id")
    private long selfId;

    @JsonProperty("post_type")
    private String postType;

    /**
     * 从 JSON 字符串中解析 LifecycleMetaEventData 对象。
     * @param json LifecycleMetaEventData 对象的 JSON 字符串
     * @return LifecycleMetaEventData 对象
     */
    public static LifecycleMetaEventData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, LifecycleMetaEventData.class);
    }
}
