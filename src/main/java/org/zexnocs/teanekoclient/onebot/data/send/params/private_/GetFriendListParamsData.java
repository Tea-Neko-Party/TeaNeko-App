package org.zexnocs.teanekoclient.onebot.data.send.params.private_;

import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.response.params._private.GetFriendListResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

/**
 * 获取好友列表的发送参数数据。
 * <p>对应的响应类型为 GetFriendListResponseData。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder
public class GetFriendListParamsData implements ISendParamsData<GetFriendListResponseData> {
    public final static String ACTION = "get_friend_list";

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
    public Class<GetFriendListResponseData> getResponseDataType() {
        return GetFriendListResponseData.class;
    }
}