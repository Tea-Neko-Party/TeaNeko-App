package org.zexnocs.teanekocore.command.api;

import lombok.Getter;

/**
 * 指令权限枚举，用于定义指令的权限等级。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Getter
public enum CommandPermission {
    /// 默认权限。用于给子指令是否遵循父指令的权限
    /// 如果父指令的权限为 DEFAULT，那么该指令不可用
    DEFAULT(0),

    /// 只允许 DEBUG 人员使用
    DEBUG(1),

    /// 允许 群主 + DEBUG 人员使用
    OWNER(2),

    /// 允许 群主 + 群管 + DEBUG 人员使用
    ADMIN(3),

    /// 允许 群主 + 群管 + DEBUG 人员 + 群成员使用
    MEMBER(4),

    /// 允许所有人使用
    ALL(5);

    /// 权限等级，数值越大权限越低
    private final int level;

    CommandPermission(int level) {
        this.level = level;
    }
}
