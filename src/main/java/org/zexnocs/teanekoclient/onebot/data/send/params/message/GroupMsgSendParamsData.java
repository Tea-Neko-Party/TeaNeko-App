package org.zexnocs.teanekoclient.onebot.data.send.params.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;

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
    private List<? extends ITeaNekoMessage> messageList;

    @Override
    public String getAction() {
        return ACTION;
    }

    /**
     * 使用新的消息列表创建一个新的参数数据对象。
     *
     * @param newMessageSubDataList 新的消息列表
     * @return {@link IMessageSendParamsData }
     */
    @Override
    public IMessageSendParamsData withMessage(List<? extends ITeaNekoMessage> newMessageSubDataList) {
        return this.toBuilder()
                .messageList(newMessageSubDataList)
                .build();
    }
}