package org.zexnocs.teanekoclient.onebot.data.response.params;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotContent;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotSenderData;

import java.util.List;

/**
 * 根据 message id 获取到群消息的响应数据的子数据
 * <br>2026/11/02：保留实现端返回的顶层群号，用于消息来源校验。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 * @version 4.6.0-l
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class GroupMsgResponseData {
    /** 2026/11/02：部分 OneBot 实现返回顶层群号，缺失时不得猜测消息所属群。 */
    @JsonProperty("group_id")
    private Long groupId;

    /// time 发送时间
    @JsonProperty("time")
    private long time;

    /// message_id 消息ID
    @JsonProperty("message_id")
    private long messageId;

    /// real_id 消息的真实ID。一般与 message_id 相同
    @JsonProperty("real_id")
    private long realId;

    /// message_type 消息类型。包含 "private", "group"
    @JsonProperty("message_type")
    private String messageType;

    /// sender 发送者信息
    @JsonProperty("sender")
    OnebotSenderData sender;

    /// message 消息数组
    @JsonProperty("message")
    private List<OnebotContent> message;
}
