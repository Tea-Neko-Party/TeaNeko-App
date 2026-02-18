package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;

/**
 * 指令范围管理器接口。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
public interface ICommandScopeManager {
    /**
     * 一般区域检查。
     */
    boolean inScope(Command command, CommandData<?> commandData);

    /**
     * 区域检查。
     * @param command 指令注解
     * @param defaultCommand 默认指令注解
     * @param commandData 指令数据
     */
    boolean inScope(Command command, DefaultCommand defaultCommand, CommandData<?> commandData);

    /**
     * 区域检查。
     * @param command 指令注解
     * @param subCommand 子指令注解
     * @param commandData 指令数据
     */
    boolean inScope(Command command, SubCommand subCommand, CommandData<?> commandData);
}
