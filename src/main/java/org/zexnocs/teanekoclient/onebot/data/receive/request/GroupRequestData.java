package org.zexnocs.teanekoclient.onebot.data.receive.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import tools.jackson.databind.ObjectMapper;

/**
 * 机器人作为管理员时收到的入群请求数据。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Getter
public class GroupRequestData {
    /**
     * 请求类型。
     * 如果是机器人作为管理员收到入群邀请，则 subType 为 "add"
     * 如果是机器人被邀请入群，则 subType 为 "invite"
     */
    @JsonProperty("sub_type")
    private String subType;

    /**
     * 请求者 QQ 号。
     * 如果是 add，则是请求者的 QQ 号
     * 如果是 invite，则是邀请者的 QQ 号
     */
    @JsonProperty("user_id")
    private long userId;

    /**
     * 群号。
     * 如果是 add，则是请求的群号
     * 如果是 invite，则是邀请的群号
     */
    @JsonProperty("group_id")
    private long groupId;

    /**
     * 邀请者 QQ 号
     * 如果是 add，则是邀请者的 QQ 号；没有则是 0
     * 如果是 invite，则是 0
     */
    @JsonProperty("invitor_id")
    private long invitorId;

    /**
     * 为 "group"
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
     * 机器人 QQ 号
     */
    @JsonProperty("self_id")
    private long selfId;

    /**
     * 为request。
     */
    @JsonProperty("post_type")
    private String postType;

    public static GroupRequestData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, GroupRequestData.class);
    }
}
