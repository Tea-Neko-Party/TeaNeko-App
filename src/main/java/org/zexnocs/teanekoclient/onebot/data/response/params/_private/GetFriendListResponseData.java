package org.zexnocs.teanekoclient.onebot.data.response.params._private;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;


/**
 * 获取好友列表响应子数据类
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder
public class GetFriendListResponseData {
    /// 用户的 QQ 号
    @JsonProperty("user_id")
    private long userId;

    /// 用户的 QID
    @JsonProperty("q_id")
    private String qId;

    /// 用户的昵称
    @JsonProperty("nickname")
    private String nickname;

    /// 用户的备注
    @JsonProperty("remark")
    private String remark;

    /// 分组信息
    @JsonProperty("group")
    private Group group;

    @Getter
    @Builder
    public static class Group {
        // 分组 ID
        @JsonProperty("group_id")
        private int groupId;

        // 分组名称
        @JsonProperty("group_name")
        private String groupName;
    }
}
