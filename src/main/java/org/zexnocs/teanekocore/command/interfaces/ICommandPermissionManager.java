package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;

/**
 * 指令权限管理器接口，用于判断当前用户是否具有执行某个指令的权限，以及管理用户的权限。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
public interface ICommandPermissionManager {
    /**
     * 一般指令权限检查。
     *
     * @param command     指令注解
     * @param commandData 指令数据
     * @return boolean    是否具有权限执行该指令
     */
    boolean hasPermission(Command command, CommandData<?> commandData);

    /**
     * 默认指令权限检查。
     *
     * @param command        指令注解
     * @param defaultCommand 默认指令注解
     * @param commandData    指令数据
     * @return boolean       是否具有权限执行该指令
     */
    boolean hasPermission(Command command, DefaultCommand defaultCommand, CommandData<?> commandData);

    /**
     * 子指令权限检查。
     *
     * @param command     指令注解
     * @param subCommand  子指令注解
     * @param commandData 指令数据
     * @return boolean    是否具有权限执行该指令
     */
    boolean hasPermission(Command command, SubCommand subCommand, CommandData<?> commandData);

    /**
     * 给指定的用户添加一个权限。
     *
     * @param userId       用户 ID
     * @param permissionId 权限 ID
     */
    void addPermission(String userId, String permissionId);

    /**
     * 给指定的用户删除一个权限。
     *
     * @param userId       用户 ID
     * @param permissionId 权限 ID
     */
    void removePermission(String userId, String permissionId);

    /**
     * 禁止指定的用户的一个权限。
     *
     * @param userId       用户 ID
     * @param permissionId 权限 ID
     */
    void banPermission(String userId, String permissionId);

    /**
     * 解除禁止指定的用户的一个权限。
     *
     * @param userId       用户 ID
     * @param permissionId 权限 ID
     */
    void unbanPermission(String userId, String permissionId);
}
