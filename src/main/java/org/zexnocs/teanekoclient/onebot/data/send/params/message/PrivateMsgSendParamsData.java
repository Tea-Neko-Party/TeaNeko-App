package org.zexnocs.teanekoclient.onebot.data.send.params.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessage;

import java.util.List;

/**
 * 发送私聊消息的参数数据。
 * <p>对应的响应类型为 Map。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder(toBuilder = true)
public class PrivateMsgSendParamsData implements IMessageSendParamsData {
    public final static String ACTION = "send_private_msg";

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("message")
    private List<OnebotMessage> messageSubDataList;

    @Override
    public String getAction() {
        return ACTION;
    }

    @Override
    public IMessageSendParamsData withMessage(List<OnebotMessage> newMessageSubDataList) {
        return this.toBuilder()
                .messageSubDataList(newMessageSubDataList)
                .build();
    }
}