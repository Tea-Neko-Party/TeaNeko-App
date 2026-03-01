package org.zexnocs.teanekoclient.onebot.data.receive.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * onebot Message 的响应数据。
 * 一个 Message 里包含多种消息类型，即 MessageSubData。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnebotMessageData {
    // ----- 一般字段 -----
    /// self_id 机器人QQ号
    @JsonProperty("self_id")
    private long selfId;

    /// user_id 发送用户QQ号
    @JsonProperty("user_id")
    private long userId;

    /// time 发送时间
    @JsonProperty("time")
    private long time;

    /// message_id 消息ID
    @JsonProperty("message_id")
    private long messageId;

    /// real_id 消息的真实ID。一般与 message_id 相同
    @JsonProperty("real_id")
    private long realId;

    /// message_seq 消息序号。一般与 message_id 相同
    @JsonProperty("message_seq")
    private long messageSeq;

    /// message_type 消息类型。包含 "private", "group"
    @JsonProperty("message_type")
    private String messageType;

    /// sender 发送者信息
    @JsonProperty("sender")
    OnebotSenderData sender;

    /// raw_message 原始消息内容
    @JsonProperty("raw_message")
    private String rawMessage;

    /// font 字体
    @JsonProperty("font")
    private int font;

    /**
     * 消息子类型。
     * "group"中：
     *     "normal"：群内普通信息
     * "private"中：
     *    "group" 表示临时对话
     *    "friend" 表示好友信息
     */
    @JsonProperty("sub_type")
    private String subType;

    /// message_format 消息格式，例如 "array"
    @JsonProperty("message_format")
    private String message_format;

    /// post_type 数据类型，例如 "message"
    @JsonProperty("post_type")
    private String postType;

    /// message 消息数组
    @JsonProperty("message")
    private List<OnebotMessage> message;

    @JsonProperty("real_seq")
    private Long realSeq;

    // ----- 群聊字段 -----
    /// group_id 群号
    @JsonProperty("group_id")
    private long groupId;

    // ----- 临时对话字段 -----
    /// temp_source 临时对话来源
    @JsonProperty("temp_source")
    private int tempSource;

    // ----- Lagrange OneBot 扩展字段 -----
    /// anonymous 匿名信息
    @JsonProperty("anonymous")
    private String anonymous;

    /// target_id 目标ID
    @JsonProperty("target_id")
    private long targetId;

    /// message_style 消息样式
    @JsonProperty("message_style")
    private Map<String, String> messageStyle;

    /**
     * 从 json 字符串中解析出 MessageReceiveData 对象。
     * @param json json 字符串
     * @return MessageReceiveData 对象
     */
    public static OnebotMessageData fromJson(String json, ObjectMapper mapper) {
        return mapper.readValue(json, OnebotMessageData.class);
    }
}
