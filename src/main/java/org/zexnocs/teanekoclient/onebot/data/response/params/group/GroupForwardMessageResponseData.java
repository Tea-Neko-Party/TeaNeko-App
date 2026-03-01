package org.zexnocs.teanekoclient.onebot.data.response.params.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 群消息转发响应子数据
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GroupForwardMessageResponseData {

    @JsonProperty("message_id")
    private long messageId;

    @JsonProperty("forward_id")
    private String forwardId;

    @JsonProperty("result")
    private long result;

    @JsonProperty("errMsg")
    private String errMsg;
}
