package org.zexnocs.teanekoclient.onebot.data.receive.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Onebot 消息发送者原生数据类，包含了发送者的基本信息和在群内的角色等信息。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OnebotSenderData {
    // ----- 通用参数 -----
    /// user_id 用户QQ号
    @JsonProperty("user_id")
    @Builder.Default
    private long userId = -1;

    /// nickname 昵称
    @JsonProperty("nickname")
    @Builder.Default
    private String nickname = null;

    /// card 群名片／备注
    @JsonProperty("card")
    @Builder.Default
    private String card = null;

    // ----- 群内发送者参数 -----
    /// role 用户在群内的角色，包括 "owner", "admin", "member"
    @JsonProperty("role")
    @Builder.Default
    private String role = "member";

    /// title 用户在群内的头衔
    @JsonProperty("title")
    @Builder.Default
    private String title = null;

    // ----- 群内临时会话发送者参数 -----
    /// group_id 群号
    @JsonProperty("group_id")
    @Builder.Default
    private int groupId = -1;

    // ----- Lagrange 扩展 -----
    /// 性别
    @JsonProperty("sex")
    @Builder.Default
    private String sex = null;

    /// 年龄
    @JsonProperty("age")
    @Builder.Default
    private int age = -1;

    /// 地区
    @JsonProperty("area")
    @Builder.Default
    private String area = null;

    /// 群等级
    @JsonProperty("level")
    @Builder.Default
    private String level = null;

    /**
     * 获取用户在群内的角色。
     * @return 用户在群内的角色
     */
    @JsonIgnore
    public Role getEnumRole() {
        return Role.fromString(role);
    }

    @Getter
    public enum Role {
        OWNER("owner"),
        ADMIN("admin"),
        MEMBER("member");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public static Role fromString(String value) {
            // 如果 value 为 null，则返回 MEMBER
            if (value == null) {
                return MEMBER;
            }

            for (Role role : Role.values()) {
                if (role.value.equals(value)) {
                    return role;
                }
            }

            // 默认返回 MEMBER
            return MEMBER;
        }
    }
}
