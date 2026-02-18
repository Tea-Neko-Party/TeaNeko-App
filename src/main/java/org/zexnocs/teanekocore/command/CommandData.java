package org.zexnocs.teanekocore.command;

import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;

/**
 * 指令的数据类
 *
 * @param <T> 指令被解析前的数据类型
 * @author zExNocs
 * @date 2026/02/18
 */
@Getter
@Builder
public class CommandData<T> {
    /// 指令体
    private final String body;

    /// 指令参数
    private final String[] args;

    /// 指令的实际作用域
    private final CommandScope scope;

    /// 指令发送者的实际权限
    private final CommandPermission permission;

    /// 指令发送者的识别 ID。
    private final String senderId;

    /// 作用域的ID。
    /// 例如如果是群聊，则是group-群号；如果是私聊，则是private-QQ号
    private final String scopeId;

    /// 指令被解析前的数据
    private final T rawData;
}
