package org.zexnocs.teanekoclient.onebot.data.send.params.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.response.params.group.GroupListResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

/**
 * 获取群列表的发送参数数据。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder
public class GetGroupListParamsData implements ISendParamsData<GroupListResponseData> {
    public final static String ACTION = "get_group_list";

    @JsonProperty("no_cache")
    private boolean noCache;

    /**
     * 获取发送参数数据的动作。
     *
     * @return 发送参数数据的动作。
     */
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
    public Class<GroupListResponseData> getResponseDataType() {
        return GroupListResponseData.class;
    }
}
