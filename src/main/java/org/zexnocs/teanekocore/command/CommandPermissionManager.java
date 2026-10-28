package org.zexnocs.teanekocore.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.easydata.CommandEasyData;
import org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseTaskConfig;

import java.util.concurrent.CompletionStage;

/**
 * 命令权限管理器
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service("commandPermissionManager")
public class CommandPermissionManager implements ICommandPermissionManager {
    public static final String ENABLE_NAMESPACE = "command.permission.enable";
    public static final String DISABLE_NAMESPACE = "command.permission.disable";

    /**
     * 添加权限
     *
     * @param userId 用户 ID
     * @param permissionId 权限 ID
     * @param scopeId 本次操作所属作用域
     */
    @Override
    public void addPermission(String userId, String scopeId, String permissionId) {
        permissionTask(ENABLE_NAMESPACE, userId, scopeId, permissionId, true, "添加权限").push();
    }

    /**
     * 授予指定用户在原作用域中的权限，等待持久化与缓存更新后才结束完成阶段。
     *
     * @param userId 原业务边界中的目标用户标识
     * @param scopeId 本次操作所属作用域
     * @param permissionId 原权限体系中的权限标识
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    @Override
    public CompletionStage<Void> addPermissionAsync(String userId, String scopeId, String permissionId) {
        return permissionTask(ENABLE_NAMESPACE, userId, scopeId, permissionId, true, "添加权限").pushAsync();
    }

    /**
     * 删除权限
     *
     * @param userId 用户 ID
     * @param permissionId 权限 ID
     * @param scopeId 本次操作所属作用域
     */
    @Override
    public void removePermission(String userId, String scopeId, String permissionId) {
        permissionTask(ENABLE_NAMESPACE, userId, scopeId, permissionId, false, "删除权限").push();
    }

    /**
     * 撤销授予指定用户在原作用域中的权限，等待持久化与缓存更新后才结束完成阶段。
     *
     * @param userId 原业务边界中的目标用户标识
     * @param scopeId 本次操作所属作用域
     * @param permissionId 原权限体系中的权限标识
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    @Override
    public CompletionStage<Void> removePermissionAsync(String userId, String scopeId, String permissionId) {
        return permissionTask(ENABLE_NAMESPACE, userId, scopeId, permissionId, false, "删除权限").pushAsync();
    }

    /**
     * 禁止权限
     *
     * @param userId 用户 ID
     * @param permissionId 权限 ID
     * @param scopeId 本次操作所属作用域
     */
    @Override
    public void banPermission(String userId, String scopeId, String permissionId) {
        permissionTask(DISABLE_NAMESPACE, userId, scopeId, permissionId, true, "禁止权限").push();
    }

    /**
     * 禁止指定用户在原作用域中的权限，等待持久化与缓存更新后才结束完成阶段。
     *
     * @param userId 原业务边界中的目标用户标识
     * @param scopeId 本次操作所属作用域
     * @param permissionId 原权限体系中的权限标识
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    @Override
    public CompletionStage<Void> banPermissionAsync(String userId, String scopeId, String permissionId) {
        return permissionTask(DISABLE_NAMESPACE, userId, scopeId, permissionId, true, "禁止权限").pushAsync();
    }

    /**
     * 解除禁止权限
     *
     * @param userId 用户 ID
     * @param permissionId 权限 ID
     * @param scopeId 本次操作所属作用域
     */
    @Override
    public void unbanPermission(String userId, String scopeId, String permissionId) {
        permissionTask(DISABLE_NAMESPACE, userId, scopeId, permissionId, false, "解除禁止权限").push();
    }

    /**
     * 解除禁止指定用户在原作用域中的权限，等待持久化与缓存更新后才结束完成阶段。
     *
     * @param userId 原业务边界中的目标用户标识
     * @param scopeId 本次操作所属作用域
     * @param permissionId 原权限体系中的权限标识
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     */
    @Override
    public CompletionStage<Void> unbanPermissionAsync(String userId, String scopeId, String permissionId) {
        return permissionTask(DISABLE_NAMESPACE, userId, scopeId, permissionId, false, "解除禁止权限").pushAsync();
    }

    /**
     * 判断是否有权限
     *
     * @param command 命令
     * @param commandData 命令数据
     * @return 当前可信操作者按原命令规则是否具有执行权限
     */
    @Override
    public boolean hasPermission(Command command, CommandData<?> commandData) {
        return __hasPermission(commandData, command.permission(), command.permissionPackage());
    }

    /**
     * 判断是否有权限
     *
     * @param command 命令
     * @param defaultCommand 默认命令
     * @param commandData 命令数据
     * @return 当前可信操作者按原命令规则是否具有执行权限
     */
    @Override
    public boolean hasPermission(Command command, DefaultCommand defaultCommand, CommandData<?> commandData) {
        // 获取权限包
        var permissionPackage = defaultCommand.permissionPackage();
        // 如果没有特殊设置权限包，则使用默认权限包
        if(permissionPackage == null || permissionPackage.length == 0) {
            permissionPackage = command.permissionPackage();
        }
        // 获取权限。如果 defaultCommand 没有设置权限，则使用 command 的权限
        var expectedPermission = defaultCommand.permission();
        if(expectedPermission.equals(CommandPermission.DEFAULT)) {
            expectedPermission = command.permission();
        }
        return __hasPermission(commandData, expectedPermission, permissionPackage);
    }

    /**
     * 判断是否有权限
     *
     * @param command 命令
     * @param subCommand 子命令
     * @param commandData 命令数据
     * @return 当前可信操作者按原命令规则是否具有执行权限
     */
    @Override
    public boolean hasPermission(Command command, SubCommand subCommand, CommandData<?> commandData) {
        // 获取权限包
        var permissionPackage = subCommand.permissionPackage();
        // 如果没有特殊设置权限包，则使用默认权限包
        if(permissionPackage == null || permissionPackage.length == 0) {
            permissionPackage = command.permissionPackage();
        }
        var expectedPermission = subCommand.permission();
        // 如果 subCommand 没有设置权限，则使用 command 的权限
        if(expectedPermission.equals(CommandPermission.DEFAULT)) {
            expectedPermission = command.permission();
        }
        return __hasPermission(commandData, expectedPermission, permissionPackage);
    }

    /**
     * 判断是否有权限
     *
     * @param commandData 命令数据
     * @param expectedPermission 预期权限
     * @param permissionPackage 权限包
     * @return 结合角色、附加授权与禁止规则得到的权限判断
     */
    private boolean __hasPermission(CommandData<?> commandData, CommandPermission expectedPermission, String[] permissionPackage) {
        // 1. 先判断是否被被取消权限，如果被禁止使用权限则直接返回 false
        for(var permissionName: permissionPackage) {
            if(permissionFlag(DISABLE_NAMESPACE, commandData.getSenderId(), commandData.getScopeId(), permissionName)) {
                return false;
            }
        }
        var actualPermission = commandData.getPermission();
        // 2. 再判断是否有权限
        // a. 如果是 DEBUG 指令，且用户不是 DBUG 权限，则直接返回 false
        if(expectedPermission.equals(CommandPermission.DEBUG) &&
           !actualPermission.equals(CommandPermission.DEBUG)) {
            return false;
        }

        // b. 判断原始权限
        if(expectedPermission.getLevel() >= actualPermission.getLevel()) {
            return true;
        }
        // c. 判断数据库权限
        for(var permissionName: permissionPackage) {
            if(permissionFlag(ENABLE_NAMESPACE, commandData.getSenderId(), commandData.getScopeId(), permissionName)) {
                return true;
            }
        }
        // c. 如果没有权限，则返回 false
        return false;
    }

    /**
     * 构造与当前读取布局一致的授权或禁止写任务，提交前不修改既有有效权限。
     *
     * @param namespace 原存储协议使用的命名空间
     * @param userId 原业务边界中的目标用户标识
     * @param scopeId 本次操作所属作用域
     * @param permissionId 原权限体系中的权限标识
     * @param value 待处理的值
     * @param name 稳定名称
     * @return 满足本方法约束的 {@code IDatabaseTaskConfig} 结果
     */
    private IDatabaseTaskConfig permissionTask(String namespace, String userId, String scopeId,
                                               String permissionId, boolean value, String name) {
        return CommandEasyData.of(namespace).get(scopeId + "@" + permissionId)
                .getTaskConfig(name).setBoolean(userId, value);
    }

    /**
     * 先读取当前用户键，缺失才兼容旧布局，显式 false 覆盖历史授权或禁止。
     *
     * @param namespace 原存储协议使用的命名空间
     * @param userId 原业务边界中的目标用户标识
     * @param scopeId 本次操作所属作用域
     * @param permissionId 原权限体系中的权限标识
     * @return 当前校验或原子操作是否满足本方法约定的条件
     */
    private boolean permissionFlag(String namespace, String userId, String scopeId, String permissionId) {
        var store = CommandEasyData.of(namespace);
        var current = store.get(scopeId + "@" + permissionId);
        if (current.has(userId)) {
            // 显式 false 也覆盖旧布局的 true，避免旧记录恢复已撤销的授权/禁止。
            return current.getBoolean(userId);
        }
        // 兼容原检查器读取的 target=permissionId, key=scopeId@userId 布局。
        return store.get(permissionId).getBoolean(scopeId + "@" + userId);
    }
}
