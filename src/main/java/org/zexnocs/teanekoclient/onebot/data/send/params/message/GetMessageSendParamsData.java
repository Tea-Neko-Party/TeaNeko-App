package org.zexnocs.teanekoclient.onebot.data.send.params.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.response.params.group.GroupMsgResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

/**
 * 获取消息的发送参数数据。
 * <p>对应的响应类型为 Map。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder
public class GetMessageSendParamsData implements ISendParamsData<GroupMsgResponseData> {
    public final static String ACTION = "get_msg";

    @JsonProperty("message_id")
    private long messageId;

    @Override
    public String getAction() {
        return ACTION;
    }

    /**
     * 获取反应数据的类型。
     *
     * @return 反应数据的类型。
     */
    @Override
    public Class<GroupMsgResponseData> getResponseDataType() {
        return GroupMsgResponseData.class;
    }
}