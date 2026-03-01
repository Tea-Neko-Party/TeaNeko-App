package org.zexnocs.teanekoclient.onebot.data.send.params.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.response.params.group.GroupMemberResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

/**
 * 获取群成员信息的发送参数数据。
 * <p>对应的响应类型为 GroupMemberResponseData。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder
public class GetGroupMemberInfoParamsData implements ISendParamsData<GroupMemberResponseData> {
    public final static String ACTION = "get_group_member_info";

    @JsonProperty("group_id")
    private final long groupId;

    @JsonProperty("user_id")
    private final long userId;

    @JsonProperty("no_cache")
    private final boolean noCache;

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
    public Class<GroupMemberResponseData> getResponseDataType() {
        return GroupMemberResponseData.class;
    }
}