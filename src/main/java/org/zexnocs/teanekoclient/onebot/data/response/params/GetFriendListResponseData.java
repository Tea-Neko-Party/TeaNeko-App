package org.zexnocs.teanekoclient.onebot.data.response.params;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


/**
 * 获取好友列表响应子数据类
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
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
    @Setter
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {
        // 分组 ID
        @JsonProperty("group_id")
        private int groupId;

        // 分组名称
        @JsonProperty("group_name")
        private String groupName;
    }
}
