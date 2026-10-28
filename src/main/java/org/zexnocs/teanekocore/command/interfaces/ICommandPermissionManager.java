package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;

import java.util.concurrent.CompletionStage;

/**
 * 指令权限管理器接口，用于判断当前用户是否具有执行某个指令的权限，以及管理用户的权限。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface ICommandPermissionManager {
    /**
     * 等待权限写入及缓存更新完成；失败由调用方处理。
     *
     * @param userId 原业务边界中的目标用户标识
     * @param scopeId 本次操作所属作用域
     * @param permissionId 原权限体系中的权限标识
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    CompletionStage<Void> addPermissionAsync(String userId, String scopeId, String permissionId);

    /**
     * 撤销授予指定用户在原作用域中的权限，等待持久化与缓存更新后才结束完成阶段。
     *
     * @param userId 原业务边界中的目标用户标识
     * @param scopeId 本次操作所属作用域
     * @param permissionId 原权限体系中的权限标识
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    CompletionStage<Void> removePermissionAsync(String userId, String scopeId, String permissionId);

    /**
     * 禁止指定用户在原作用域中的权限，等待持久化与缓存更新后才结束完成阶段。
     *
     * @param userId 原业务边界中的目标用户标识
     * @param scopeId 本次操作所属作用域
     * @param permissionId 原权限体系中的权限标识
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    CompletionStage<Void> banPermissionAsync(String userId, String scopeId, String permissionId);

    /**
     * 解除禁止指定用户在原作用域中的权限，等待持久化与缓存更新后才结束完成阶段。
     *
     * @param userId 原业务边界中的目标用户标识
     * @param scopeId 本次操作所属作用域
     * @param permissionId 原权限体系中的权限标识
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    CompletionStage<Void> unbanPermissionAsync(String userId, String scopeId, String permissionId);

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
     * @param scopeId      所处范围 ID
     * @param permissionId 权限 ID
     */
    void addPermission(String userId, String scopeId, String permissionId);

    /**
     * 给指定的用户删除一个权限。
     *
     * @param userId       用户 ID
     * @param scopeId      所处范围 ID
     * @param permissionId 权限 ID
     */
    void removePermission(String userId, String scopeId, String permissionId);

    /**
     * 禁止指定的用户的一个权限。
     *
     * @param userId       用户 ID
     * @param scopeId      所处范围 ID
     * @param permissionId 权限 ID
     */
    void banPermission(String userId, String scopeId, String permissionId);

    /**
     * 解除禁止指定的用户的一个权限。
     *
     * @param userId       用户 ID
     * @param scopeId      所处范围 ID
     * @param permissionId 权限 ID
     */
    void unbanPermission(String userId, String scopeId, String permissionId);
}
