package org.zexnocs.teanekoplugin.onebot.common;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContentPart;
import org.zexnocs.teanekoclient.onebot.core.OnebotDebuggerService;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupBanSender;
import org.zexnocs.teanekoclient.onebot.sender.message.DeleteMessageSender;
import org.zexnocs.teanekoclient.onebot.sender.message.OnebotGetMsgSender;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 消息撤回命令。
 *
 * <br>2026/11/04：增加中文指令显示名，供控制台目录与选择卡片使用。
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * <br>2026/11/07：异步副作用前复核固定插件代次，停用后拒绝迟到业务。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.1
 * @version 4.9.0-l
 */
@Description("撤回成员消息。只有拥有相关权限的人才可以使用。")
// 2026/11/04：显示名与稳定指令前缀分别维护。
@Command(displayName = "撤回消息", value = {"/delete", "/撤回"},
        permissionPackage = "permission.group.manager-delete",
        scope = CommandScope.GROUP,
        permission = CommandPermission.ADMIN,
        supportedClients = {OnebotTeaNekoClient.class})
@PluginOwned("onebot.common")
public class MessageDeleteCommand {
    // 2026/11/07：异步业务固定入口代次，停用后不启动后续副作用。
    private final PluginAccess plugins;
    private final DeleteMessageSender deleteMessageSender;
    private final OnebotGetMsgSender getMsgSender;
    private final GroupBanSender groupBanSender;
    private final OnebotDebuggerService onebotDebuggerService;

    /**
     * 组合消息查询、撤回、禁言及 DEBUG 保护服务，沿原平台规则处理引用目标。
     *
     * @param deleteMessageSender 由装配方提供的 {@code DeleteMessageSender} 协作依赖
     * @param getMsgSender 由装配方提供的 {@code OnebotGetMsgSender} 协作依赖
     * @param groupBanSender 由装配方提供的 {@code GroupBanSender} 协作依赖
     * @param onebotDebuggerService 由装配方提供的 {@code OnebotDebuggerService} 协作依赖
     * @param plugins 统一插件代次访问入口
     */
    @Autowired
    public MessageDeleteCommand(DeleteMessageSender deleteMessageSender,
                                OnebotGetMsgSender getMsgSender,
                                GroupBanSender groupBanSender, OnebotDebuggerService onebotDebuggerService, PluginAccess plugins) {
        this.plugins = plugins;
        this.deleteMessageSender = deleteMessageSender;
        this.getMsgSender = getMsgSender;
        this.groupBanSender = groupBanSender;
        this.onebotDebuggerService = onebotDebuggerService;
    }

    /**
     * 将回复的消息撤回；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("将回复的消息撤回。")
    @DefaultCommand
    public CompletionStage<Void> delete(CommandData<OnebotMessageData> commandData) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("onebot.common");
        var data = commandData.getRawData();
        var messages = data.getMessages();
        var source = data.getOnebotRawMessageData();
        if (messages.isEmpty() || !(messages.getFirst().getContentPart() instanceof IReplyTeaNekoContentPart reply)) {
            return CompletableFuture.completedStage(null);
        }
        return getMsgSender.getMsg(reply.getId()).asStage().thenCompose(result -> {
            plugins.check(pluginToken);
            if (!result.isSuccess()) {
                return data.getMessageSender().sendReplyMessageAsync("获取消息信息失败，可能该消息已经被撤回。");
            }
            var response = result.getResult();
            if (response == null) return data.getMessageSender().sendReplyMessageAsync("意外的错误：获取消息数据为 NULL。");
            if (onebotDebuggerService.isDebugger(Long.parseLong(response.getUserData().getUserIdInPlatform()))) {
                return data.getMessageSender().sendAtReplyMessageAsync("大胆喵！无耻狂徒竟敢撤回群主的消息！")
                        .thenCompose(ignored -> {
                            plugins.check(pluginToken);
                            return groupBanSender.banAsync(source.getGroupId(), source.getUserId(), 60);
                        });
            }
            return deleteMessageSender.deleteAsync(Long.parseLong(reply.getId()));
        });
    }
}
