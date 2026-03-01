package org.zexnocs.teanekoclient.onebot.data.receive.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import tools.jackson.databind.ObjectMapper;

/**
 * 加好友请求数据类
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Getter
public class PrivateRequestData {
    /**
     * 请求者QQ
     */
    @JsonProperty("user_id")
    private long userId;

    /**
     * 为 "friend"
     */
    @JsonProperty("request_type")
    private String requestType;

    /**
     * 请求理由
     */
    @JsonProperty("comment")
    private String comment;

    /**
     * flag
     */
    @JsonProperty("flag")
    private String flag;

    /**
     * 请求时间戳
     */
    @JsonProperty("time")
    private long time;

    /**
     * 机器人QQ
     */
    @JsonProperty("self_id")
    private long selfId;

     /**
     * 为 "request"
     */
    @JsonProperty("post_type")
    private String postType;

    public static PrivateRequestData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, PrivateRequestData.class);
    }
}
