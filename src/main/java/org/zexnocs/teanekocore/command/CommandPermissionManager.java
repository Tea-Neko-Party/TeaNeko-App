package org.zexnocs.teanekocore.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.easydata.CommandEasyData;
import org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager;

/**
 * 命令权限管理器
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
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
     */
    @Override
    public void addPermission(String userId, String scopeId, String permissionId) {
        // 获取 EasyData 的任务配置
        var task = CommandEasyData.of(ENABLE_NAMESPACE).get(scopeId + "@" + permissionId).getTaskConfig("添加权限");
        // 设置用户的权限为 true
        task.setBoolean(userId, true);
        // 推送任务
        task.push();
    }

    /**
     * 删除权限
     *
     * @param userId 用户 ID
     * @param permissionId 权限 ID
     */
    @Override
    public void removePermission(String userId, String scopeId, String permissionId) {
        // 获取 EasyData 的任务配置
        var task = CommandEasyData.of(ENABLE_NAMESPACE).get(scopeId + "@" + permissionId).getTaskConfig("删除权限");
        // 设置用户的权限为 false
        task.setBoolean(userId, false);
        // 推送任务
        task.push();
    }

    /**
     * 禁止权限
     *
     * @param userId 用户 ID
     * @param permissionId 权限 ID
     */
    @Override
    public void banPermission(String userId, String scopeId, String permissionId) {
        // 获取 EasyData 的任务配置
        var task = CommandEasyData.of(DISABLE_NAMESPACE).get(scopeId + "@" + permissionId).getTaskConfig("禁止权限");
        // 设置用户的权限为 true
        task.setBoolean(userId, true);
        // 推送任务
        task.push();
    }

    /**
     * 解除禁止权限
     *
     * @param userId 用户 ID
     * @param permissionId 权限 ID
     */
    @Override
    public void unbanPermission(String userId, String scopeId, String permissionId) {
        // 获取 EasyData 的任务配置
        var task = CommandEasyData.of(DISABLE_NAMESPACE).get(scopeId + "@" + permissionId).getTaskConfig("解除禁止权限");
        // 设置用户的权限为 false
        task.setBoolean(userId, false);
        // 推送任务
        task.push();
    }

    /**
     * 判断是否有权限
     *
     * @param command 命令
     * @param commandData 命令数据
     * @return boolean
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
     * @return boolean
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
     * @return boolean
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
     * @return boolean
     */
    private boolean __hasPermission(CommandData<?> commandData, CommandPermission expectedPermission, String[] permissionPackage) {
        // 1. 先判断是否被被取消权限，如果被禁止使用权限则直接返回 false
        var __disableEasyData = CommandEasyData.of(DISABLE_NAMESPACE);
        for(var permissionName: permissionPackage) {
            if(__disableEasyData.get(permissionName)
                    .getBoolean(commandData.getScopeId() + "@" + commandData.getSenderId())) {
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
        var __enableEasyData = CommandEasyData.of(ENABLE_NAMESPACE);
        for(var permissionName: permissionPackage) {
            if(__enableEasyData.get(permissionName)
                    .getBoolean(commandData.getScopeId() + "@" + commandData.getSenderId())) {
                return true;
            }
        }
        // c. 如果没有权限，则返回 false
        return false;
    }
}
