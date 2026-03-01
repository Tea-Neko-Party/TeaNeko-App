package org.zexnocs.teanekoclient.onebot.data.response.params.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 群列表响应子数据
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GroupListResponseData {
    /// 群号
    @JsonProperty("group_id")
    private long groupId;

    /// 群名称
    @JsonProperty("group_name")
    private String groupName;

    /// 群成员数量
    @JsonProperty("member_count")
    private int memberCount;

    /// 群成员上限
    @JsonProperty("max_member_count")
    private int maxMemberCount;
}
