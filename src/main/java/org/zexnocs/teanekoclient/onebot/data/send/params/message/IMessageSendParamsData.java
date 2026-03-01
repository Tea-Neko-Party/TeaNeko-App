package org.zexnocs.teanekoclient.onebot.data.send.params.message;

import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessage;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

import java.util.List;
import java.util.Map;

/**
 * 消息发送参数数据接口。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@SuppressWarnings("rawtypes")
public interface IMessageSendParamsData extends ISendParamsData<Map> {
    /**
     * 获取当前消息列表。
     * @return 当前消息列表
     */
    List<OnebotMessage> getMessageSubDataList();

    /**
     * 使用新的信息列表生成新的消息发送参数数据。
     * 不改变其他参数，例如群号、用户号等。
     * @param message 消息列表
     */
    IMessageSendParamsData withMessage(List<OnebotMessage> message);

    /**
     * 获取反应数据的类型。
     *
     * @return 反应数据的类型。
     */
    @Override
    default Class<Map> getResponseDataType() {
        return Map.class;
    }
}
