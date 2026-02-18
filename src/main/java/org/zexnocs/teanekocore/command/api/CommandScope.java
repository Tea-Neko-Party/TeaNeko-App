package org.zexnocs.teanekocore.command.api;

/**
 * 指令的作用域枚举，用于定义指令的作用范围。
 * web 交流属于 private
 *
 * @author zExNocs
 * @date 2026/02/18
 */
public enum CommandScope {
    /// 默认作用域。用于子指令是否遵循父指令的作用域
    /// 如果父指令为 DEFAULT，那么该指令则为“白名单模式”。需要使用 CommandScopeManager 来专门设置。
    DEFAULT,

    /// 群组指令，仅在群聊中生效
    GROUP,

    /// 私聊指令，仅在私聊中生效
    PRIVATE,

    /// 群组和私聊指令，在群聊和私聊中都生效
    ALL,
}
