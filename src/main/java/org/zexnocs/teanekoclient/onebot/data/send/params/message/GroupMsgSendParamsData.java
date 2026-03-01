package org.zexnocs.teanekoclient.onebot.data.send.params.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessage;

import java.util.List;

/**
 * 发送群消息的参数数据。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder(toBuilder = true)
public class GroupMsgSendParamsData implements IMessageSendParamsData {
    public final static String ACTION = "send_group_msg";

    @JsonProperty("group_id")
    private long groupId;

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