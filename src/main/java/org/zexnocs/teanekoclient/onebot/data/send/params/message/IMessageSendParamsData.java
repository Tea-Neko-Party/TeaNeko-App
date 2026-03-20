package org.zexnocs.teanekoclient.onebot.data.send.params.message;

import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoclient.onebot.data.response.params.OnebotMessageSendResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

import java.util.List;

/**
 * 消息发送参数数据接口。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
public interface IMessageSendParamsData extends ISendParamsData<OnebotMessageSendResponseData> {
    /**
     * 获取当前消息列表。
     *
     * @return 当前消息列表
     */
    List<? extends ITeaNekoContent> getMessageList();

    /**
     * 使用新的信息列表生成新的消息发送参数数据。
     * 不改变其他参数，例如群号、用户号等。
     *
     * @param message 消息列表
     */
    IMessageSendParamsData withMessage(List<? extends ITeaNekoContent> message);

    /**
     * 获取反应数据的类型。
     *
     * @return 反应数据的类型。
     */
    @Override
    default Class<OnebotMessageSendResponseData> getResponseDataType() {
        return OnebotMessageSendResponseData.class;
    }
}
