package org.zexnocs.teanekoclient.onebot.data.send.params.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupListResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

/**
 * 单群资料查询参数，复用协议中与群列表相同的响应结构。
 * @param groupId 当前宿主确认的群号
 * @param noCache 是否要求供应商绕过缓存
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public record GetGroupInfoParamsData(@JsonProperty("group_id") long groupId,
                                     @JsonProperty("no_cache") boolean noCache) implements ISendParamsData<GroupListResponseData> {
    /** @return OneBot 单群资料动作名 */
    @Override public String getAction() { return "get_group_info"; }
    /** @return 与群列表子项一致的资料类型 */
    @Override public Class<GroupListResponseData> getResponseDataType() { return GroupListResponseData.class; }
}
