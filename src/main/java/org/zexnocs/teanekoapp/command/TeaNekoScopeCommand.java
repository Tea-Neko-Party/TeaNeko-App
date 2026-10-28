package org.zexnocs.teanekoapp.command;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.CommandScopeManager;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.command.easydata.CommandEasyData;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 茶猫权限指令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.11
 * @version 4.5.0-l
 * @see CommandScopeManager
 */
@Description("debugger指令。设置指令范围的指令。")
@Command(value = "/scope",
        permission = CommandPermission.DEBUG,
        scope = CommandScope.ALL)
@RequiredArgsConstructor
public class TeaNekoScopeCommand {
    private final TeaNekoScopeService teaNekoScopeService;

    /**
     * 允许群组使用原本不可使用的命令；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param commandId 用于本次处理的 {@code commandId} 数据
     * @param groupId 目标群组标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            允许群组使用原本不可使用的命令。
            使用 `/scope add-group <commandId> <?groupId>` 来添加。
            如果 groupId 为空，则使用当前的群聊 ID""")
    @SubCommand("add-group")
    public CompletionStage<Void> addGroup(CommandData<ITeaNekoMessageData> commandData,
                         String commandId,
                         @DefaultValue("0") String groupId) {
        var data = commandData.getRawData();
        String scopeId = groupId.equals("0") ?
                commandData.getScopeId() :
                teaNekoScopeService.getGroupScopeId(data.getClient(), groupId);
        return CommandEasyData.of(CommandScopeManager.ENABLE_NAMESPACE)
                .get(commandId)
                .getTaskConfig("添加区域")
                .setBoolean(scopeId, true)
                .pushAsync();
    }

    /**
     * 删除群组使用原本不可使用的命令；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param commandId 用于本次处理的 {@code commandId} 数据
     * @param groupId 目标群组标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            删除群组使用原本不可使用的命令。
            使用 `/scope remove-group <commandId> <?groupId>` 来删除。
            如果 groupId 为空，则使用当前的群聊 ID""")
    @SubCommand("remove-group")
    public CompletionStage<Void> removeGroup(CommandData<ITeaNekoMessageData> commandData,
                            String commandId,
                            @DefaultValue("0") String groupId) {
        var data = commandData.getRawData();
        String scopeId = groupId.equals("0") ?
                commandData.getScopeId() :
                teaNekoScopeService.getGroupScopeId(data.getClient(), groupId);
        return CommandEasyData.of(CommandScopeManager.ENABLE_NAMESPACE)
                .get(commandId)
                .getTaskConfig("删除区域")
                .setBoolean(scopeId, false)
                .pushAsync();
    }

    /**
     * 禁止一个群组使用某个指令；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param commandId 用于本次处理的 {@code commandId} 数据
     * @param groupId 目标群组标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            禁止一个群组使用某个指令。
            使用 `/scope ban-group <commandId> <?groupId>` 来禁止。
            如果 groupId 为空，则使用当前的群聊 ID""")
    @SubCommand("ban-group")
    public CompletionStage<Void> banGroup(CommandData<ITeaNekoMessageData> commandData,
                         String commandId,
                         @DefaultValue("0") String groupId) {
        var data = commandData.getRawData();
        String scopeId = groupId.equals("0") ?
                commandData.getScopeId() :
                teaNekoScopeService.getGroupScopeId(data.getClient(), groupId);
        return CommandEasyData.of(CommandScopeManager.DISABLE_NAMESPACE)
                .get(commandId).getTaskConfig("禁止区域")
                .setBoolean(scopeId, true)
                .pushAsync();
    }

    /**
     * 取消禁止一个群组使用某个指令；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param commandId 用于本次处理的 {@code commandId} 数据
     * @param groupId 目标群组标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            取消禁止一个群组使用某个指令。
            使用 `/scope unban-group <commandId> <?groupId>` 来允许。
            如果 groupId 为空，则使用当前的群聊 ID""")
    @SubCommand("unban-group")
    public CompletionStage<Void> unbanGroup(CommandData<ITeaNekoMessageData> commandData,
                           String commandId,
                           @DefaultValue("0") String groupId) {
        var data = commandData.getRawData();
        String scopeId = groupId.equals("0") ?
                commandData.getScopeId() :
                teaNekoScopeService.getGroupScopeId(data.getClient(), groupId);
        return CommandEasyData.of(CommandScopeManager.DISABLE_NAMESPACE)
                .get(commandId).getTaskConfig("取消禁止区域")
                .setBoolean(scopeId, false)
                .pushAsync();
    }
}
