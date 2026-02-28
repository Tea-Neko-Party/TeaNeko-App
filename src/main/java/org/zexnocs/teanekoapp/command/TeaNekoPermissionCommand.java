package org.zexnocs.teanekoapp.command;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 用于给予、撤销权限的服务
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Description("群主指令。给予、撤销权限的指令。修改范围仅限当前群聊。")
@Command(value = {"/permission", "/权限"},
        permission = CommandPermission.OWNER,
        scope = CommandScope.ALL,
        permissionPackage = "teaneko.admin.permission")
public class TeaNekoPermissionCommand {

    private final ICommandPermissionManager iCommandPermissionManager;
    private final ITeaUserService iTeaUserService;

    public TeaNekoPermissionCommand(ICommandPermissionManager iCommandPermissionManager,
                                    ITeaUserService iTeaUserService) {
        this.iCommandPermissionManager = iCommandPermissionManager;
        this.iTeaUserService = iTeaUserService;
    }

    @Description("""
            为一个用户添加一个权限。
            格式：/permission add <user_id> <permission>
            默认使用本群""")
    @SubCommand("add")
    public void addPermission(CommandData<ITeaNekoMessageData> commandData, String userId, String permission) {
        var data = commandData.getRawData();
        var user = iTeaUserService.get(data.getClient(), userId);
        if(user == null) {
            var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
            messageSender.sendReplyMessage("用户不存在喵", data);
            return;
        }
        iCommandPermissionManager.addPermission(user.toString(), commandData.getScopeId(), permission);
    }

    @Description("""
            删除一个权限。
            格式：/permission remove <user_id> <permission>
            默认使用本群""")
    @SubCommand("remove")
    public void removePermission(CommandData<ITeaNekoMessageData> commandData, String userId, String permission) {
        var data = commandData.getRawData();
        var user = iTeaUserService.get(data.getClient(), userId);
        if(user == null) {
            var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
            messageSender.sendReplyMessage("用户不存在喵", data);
            return;
        }
        iCommandPermissionManager.removePermission(user.toString(), commandData.getScopeId(), permission);
    }

    @Description("""
            禁止一个权限。
            格式：/permission ban <user_id> <permission>
            默认使用本群""")
    @SubCommand("ban")
    public void banPermission(CommandData<ITeaNekoMessageData> commandData, String userId, String permission) {
        var data = commandData.getRawData();
        var user = iTeaUserService.get(data.getClient(), userId);
        if(user == null) {
            var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
            messageSender.sendReplyMessage("用户不存在喵", data);
            return;
        }
        iCommandPermissionManager.banPermission(user.toString(), commandData.getScopeId(), permission);
    }

    @Description("""
            解除禁止一个权限。
            格式：/permission unban <user_id> <permission>
            默认使用本群""")
    @SubCommand("unban")
    public void unbanPermission(CommandData<ITeaNekoMessageData> commandData, String userId, String permission) {
        var data = commandData.getRawData();
        var user = iTeaUserService.get(data.getClient(), userId);
        if(user == null) {
            var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
            messageSender.sendReplyMessage("用户不存在喵", data);
            return;
        }
        iCommandPermissionManager.unbanPermission(user.toString(), commandData.getScopeId(), permission);
    }
}
