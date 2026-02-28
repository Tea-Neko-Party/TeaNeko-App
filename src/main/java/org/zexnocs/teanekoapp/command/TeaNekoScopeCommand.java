package org.zexnocs.teanekoapp.command;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.CommandScopeManager;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.command.easydata.CommandEasyData;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 茶猫权限指令。
 *
 * @see CommandScopeManager
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Description("debugger指令。设置指令范围的指令。")
@Command(value = "/scope",
        permission = CommandPermission.DEBUG,
        scope = CommandScope.ALL)
public class TeaNekoScopeCommand {
    private final TeaNekoCommandConverter teaNekoCommandConverter;

    public TeaNekoScopeCommand(TeaNekoCommandConverter teaNekoCommandConverter) {
        this.teaNekoCommandConverter = teaNekoCommandConverter;
    }

    @Description("""
            允许群组使用原本不可使用的命令。
            使用 `/scope add-group <commandId> <?groupId>` 来添加。
            如果 groupId 为空，则使用当前的群聊 ID""")
    @SubCommand("add-group")
    public void addGroup(CommandData<ITeaNekoMessageData> commandData,
                         String commandId,
                         @DefaultValue("0") String groupId) {
        var data = commandData.getRawData();
        String scopeId = groupId.equals("0") ?
                commandData.getScopeId() :
                teaNekoCommandConverter.getGroupScopeId(data.getClient(), groupId);
        CommandEasyData.of(CommandScopeManager.ENABLE_NAMESPACE)
                .get(commandId)
                .getTaskConfig("添加区域")
                .setBoolean(scopeId, true)
                .push();
    }

    @Description("""
            删除群组使用原本不可使用的命令。
            使用 `/scope remove-group <commandId> <?groupId>` 来删除。
            如果 groupId 为空，则使用当前的群聊 ID""")
    @SubCommand("remove-group")
    public void removeGroup(CommandData<ITeaNekoMessageData> commandData,
                            String commandId,
                            @DefaultValue("0") String groupId) {
        var data = commandData.getRawData();
        String scopeId = groupId.equals("0") ?
                commandData.getScopeId() :
                teaNekoCommandConverter.getGroupScopeId(data.getClient(), groupId);
        CommandEasyData.of(CommandScopeManager.ENABLE_NAMESPACE)
                .get(commandId)
                .getTaskConfig("删除区域")
                .setBoolean(scopeId, false)
                .push();
    }

    @Description("""
            禁止一个群组使用某个指令。
            使用 `/scope ban-group <commandId> <?groupId>` 来禁止。
            如果 groupId 为空，则使用当前的群聊 ID""")
    @SubCommand("ban-group")
    public void banGroup(CommandData<ITeaNekoMessageData> commandData,
                         String commandId,
                         @DefaultValue("0") String groupId) {
        var data = commandData.getRawData();
        String scopeId = groupId.equals("0") ?
                commandData.getScopeId() :
                teaNekoCommandConverter.getGroupScopeId(data.getClient(), groupId);
        CommandEasyData.of(CommandScopeManager.DISABLE_NAMESPACE)
                .get(commandId).getTaskConfig("禁止区域")
                .setBoolean(scopeId, true)
                .push();
    }

    @Description("""
            取消禁止一个群组使用某个指令。
            使用 `/scope unban-group <commandId> <?groupId>` 来允许。
            如果 groupId 为空，则使用当前的群聊 ID""")
    @SubCommand("unban-group")
    public void unbanGroup(CommandData<ITeaNekoMessageData> commandData,
                           String commandId,
                           @DefaultValue("0") String groupId) {
        var data = commandData.getRawData();
        String scopeId = groupId.equals("0") ?
                commandData.getScopeId() :
                teaNekoCommandConverter.getGroupScopeId(data.getClient(), groupId);
        CommandEasyData.of(CommandScopeManager.DISABLE_NAMESPACE)
                .get(commandId).getTaskConfig("取消禁止区域")
                .setBoolean(scopeId, false)
                .push();
    }
}
