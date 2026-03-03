package org.zexnocs.teanekoclient.onebot.data.response.params;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekoapp.response.api.IMessageResponseData;

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
public class OnebotMessageResponseData implements IMessageResponseData {

    @JsonProperty("message_id")
    private Long messageId;

    @JsonProperty("forward_id")
    private String forwardId;

    @JsonProperty("result")
    private Long result;

    @JsonProperty("errMsg")
    private String errMsg;

    /**
     * 获取消息 ID。
     *
     * @return 消息 ID
     */
    @Override
    public String getMessageId() {
        if(messageId != null) {
            return messageId.toString();
        } else {
            return result == null ? null : result.toString();
        }
    }
}
