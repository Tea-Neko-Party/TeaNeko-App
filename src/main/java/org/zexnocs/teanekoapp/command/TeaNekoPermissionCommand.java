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

import java.util.concurrent.CompletionStage;

/**
 * 用于给予、撤销权限的服务
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.11
 * @version 4.5.0-l
 */
@Description("群主指令。给予、撤销权限的指令。修改范围仅限当前群聊。")
@Command(value = {"/permission", "/权限"},
        permission = CommandPermission.OWNER,
        scope = CommandScope.ALL,
        permissionPackage = "teaneko.admin.permission")
public class TeaNekoPermissionCommand {

    private final ICommandPermissionManager iCommandPermissionManager;
    private final ITeaUserService iTeaUserService;

    /**
     * 注入权限写服务与原用户目录，先解析目标用户再修改当前作用域权限。
     *
     * @param iCommandPermissionManager 本次装配使用的 {@code ICommandPermissionManager} 服务
     * @param iTeaUserService 本次装配使用的 {@code ITeaUserService} 服务
     */
    public TeaNekoPermissionCommand(ICommandPermissionManager iCommandPermissionManager,
                                    ITeaUserService iTeaUserService) {
        this.iCommandPermissionManager = iCommandPermissionManager;
        this.iTeaUserService = iTeaUserService;
    }

    /**
     * 为一个用户添加一个权限；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param userId 原业务边界中的目标用户标识
     * @param permission 需校验或修改的权限
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            为一个用户添加一个权限。
            格式：/permission add <user_id> <permission>
            默认使用本群""")
    @SubCommand("add")
    public CompletionStage<Void> addPermission(CommandData<ITeaNekoMessageData> commandData, String userId, String permission) {
        var data = commandData.getRawData();
        var user = iTeaUserService.get(data.getClient(), userId);
        if(user == null) {
            var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                    .getEasyBuilder(data);
            return messageSender.addReplyTextMessage("用户不存在喵").sendAsync();
        }
        return iCommandPermissionManager.addPermissionAsync(user.toString(), commandData.getScopeId(), permission);
    }

    /**
     * 删除一个权限；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param userId 原业务边界中的目标用户标识
     * @param permission 需校验或修改的权限
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            删除一个权限。
            格式：/permission remove <user_id> <permission>
            默认使用本群""")
    @SubCommand("remove")
    public CompletionStage<Void> removePermission(CommandData<ITeaNekoMessageData> commandData, String userId, String permission) {
        var data = commandData.getRawData();
        var user = iTeaUserService.get(data.getClient(), userId);
        if(user == null) {
            var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                    .getEasyBuilder(data);
            return messageSender.addReplyTextMessage("用户不存在喵").sendAsync();
        }
        return iCommandPermissionManager.removePermissionAsync(user.toString(), commandData.getScopeId(), permission);
    }

    /**
     * 禁止一个权限；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param userId 原业务边界中的目标用户标识
     * @param permission 需校验或修改的权限
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            禁止一个权限。
            格式：/permission ban <user_id> <permission>
            默认使用本群""")
    @SubCommand("ban")
    public CompletionStage<Void> banPermission(CommandData<ITeaNekoMessageData> commandData, String userId, String permission) {
        var data = commandData.getRawData();
        var user = iTeaUserService.get(data.getClient(), userId);
        if(user == null) {
            var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                    .getEasyBuilder(data);
            return messageSender.addReplyTextMessage("用户不存在喵").sendAsync();
        }
        return iCommandPermissionManager.banPermissionAsync(user.toString(), commandData.getScopeId(), permission);
    }

    /**
     * 解除禁止一个权限；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param userId 原业务边界中的目标用户标识
     * @param permission 需校验或修改的权限
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            解除禁止一个权限。
            格式：/permission unban <user_id> <permission>
            默认使用本群""")
    @SubCommand("unban")
    public CompletionStage<Void> unbanPermission(CommandData<ITeaNekoMessageData> commandData, String userId, String permission) {
        var data = commandData.getRawData();
        var user = iTeaUserService.get(data.getClient(), userId);
        if(user == null) {
            var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                    .getEasyBuilder(data);
            return messageSender.addReplyTextMessage("用户不存在喵").sendAsync();
        }
        return iCommandPermissionManager.unbanPermissionAsync(user.toString(), commandData.getScopeId(), permission);
    }
}
