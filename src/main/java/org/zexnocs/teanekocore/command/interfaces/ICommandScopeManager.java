package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;

/**
 * 指令范围管理器接口。用于判断当前指令是否在可执行的范围内，以及管理指令的范围。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
public interface ICommandScopeManager {
    /**
     * 一般区域检查。
     *
     * @param command       指令注解
     * @param commandData   指令数据
     * @return boolean      是否在范围内
     */
    boolean inScope(Command command, CommandData<?> commandData);

    /**
     * 区域检查。
     *
     * @param command        指令注解
     * @param defaultCommand 默认指令注解
     * @param commandData    指令数据
     * @return boolean       是否在范围内
     */
    boolean inScope(Command command, DefaultCommand defaultCommand, CommandData<?> commandData);

    /**
     * 区域检查。
     *
     * @param command     指令注解
     * @param subCommand  子指令注解
     * @param commandData 指令数据
     * @return boolean    是否在范围内
     */
    boolean inScope(Command command, SubCommand subCommand, CommandData<?> commandData);
}
