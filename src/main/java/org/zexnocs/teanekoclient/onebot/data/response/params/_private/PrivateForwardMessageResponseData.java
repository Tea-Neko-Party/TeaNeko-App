package org.zexnocs.teanekoclient.onebot.data.response.params._private;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * 私聊消息转发响应子数据
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder
public class PrivateForwardMessageResponseData {
    @JsonProperty("message_id")
    private long messageId;

    @JsonProperty("forward_id")
    private String forwardId;

    @JsonProperty("result")
    private long result;

    @JsonProperty("errMsg")
    private String errMsg;
}
