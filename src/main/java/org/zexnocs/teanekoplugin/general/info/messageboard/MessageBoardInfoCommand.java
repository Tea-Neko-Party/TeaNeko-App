package org.zexnocs.teanekoplugin.general.info.messageboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 留言信息指令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Description("获取、给予留言信息。")
@Command(value = {"/message", "/留言"},
        permission = CommandPermission.ALL,
        scope = CommandScope.GROUP,
        permissionPackage = {"info.message"})
public class MessageBoardInfoCommand {
    private final MessageBoardInfoService messageBoardInfoService;
    private final ITeaUserService iTeaUserService;

    /**
     * 注入留言写服务及用户目录，提交完成后才确认留言更新。
     *
     * @param messageBoardInfoService 由装配方提供的 {@code MessageBoardInfoService} 协作依赖
     * @param iTeaUserService 本次装配使用的 {@code ITeaUserService} 服务
     */
    @Autowired
    public MessageBoardInfoCommand(MessageBoardInfoService messageBoardInfoService, ITeaUserService iTeaUserService) {
        this.messageBoardInfoService = messageBoardInfoService;
        this.iTeaUserService = iTeaUserService;
    }

    /**
     * 获取或给予留言消息；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param targetId 原业务规则允许操作的目标标识
     * @param messages 保持原始顺序的统一消息列表
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            获取或给予留言消息。
            格式：/留言 <?目标ID> <?留言信息>
            如果留言信息为空，则获取留言信息。
            默认目标ID为当前用户""")
    @DefaultCommand
    public CompletionStage<Void> getOrGive(CommandData<ITeaNekoMessageData> commandData,
                          @DefaultValue("null") String targetId,
                          List<String> messages) {
        var data = commandData.getRawData();
        var sender = data.getUserData().getUuid();
        UUID target;
        if(targetId.equals("null")) {
            target = sender;
        } else {
            target = iTeaUserService.get(data.getClient(), targetId);
        }
        if(target == null) {
            return data.getMessageSender()
                    .sendReplyMessageAsync("用户尚未注册喵~");
        }
        if(messages.isEmpty()) {
            var list = messageBoardInfoService.getMessageList(target);
            if(list.isEmpty()) {
                return data.getMessageSender()
                        .sendReplyMessageAsync("没有留言喵~");
            }
            var builder = data.getForwardMessageSender();
            int index = 1;
            int size = list.size();
            for(var messageData: list) {
                builder.addBotText("""
                        %s
                        索引：%d / %d""".formatted(messageData.getMessage(), index, size));
                index++;
            }
            return builder.sendByPartAsync(8);
        }
        // 否则是给予留言
        var first = messages.getFirst();
        if(first.equals("给予") || first.equals("give")) {
            return data.getMessageSender().sendReplyMessageAsync("""
                请使用 "/留言 @ xxx" 来给别人留言。
                删除 "给予" 或者 "give" 即可。""");
        }
        var message = String.join(" ", messages);
        return messageBoardInfoService.setMessage(sender, target, message)
                .thenCompose(result -> data.getMessageSender().sendAtReplyMessageAsync(result));
    }

    /**
     * 格式：/留言 admin-get &lt;?目标ID&gt;；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param targetId 原业务规则允许操作的目标标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            debugger 指令。获取详细的留言信息。
            格式：/留言 admin-get <?目标ID>""")
    @SubCommand(value = {"admin-get"}, permission = CommandPermission.DEBUG)
    public CompletionStage<Void> adminGet(CommandData<ITeaNekoMessageData> commandData,
                    @DefaultValue("0") long targetId) {
        var data = commandData.getRawData();
        UUID target;
        if(targetId == 0) {
            target = data.getUserData().getUuid();
        } else {
            target = iTeaUserService.get(data.getClient(), String.valueOf(targetId));
        }
        if(target == null) {
            return data.getMessageSender()
                    .sendReplyMessageAsync("用户尚未注册喵~");
        }

        var list = messageBoardInfoService.getMessageList(target);
        if(list.isEmpty()) {
            return data.getMessageSender().sendReplyMessageAsync("没有留言喵~");
        }
        var builder = data.getForwardMessageSender();
        for(var message: list) {
            var sender = data.getUserData().getUuid();
            String senderPlatformId = iTeaUserService.getPlatformId(data.getClient(), sender);
            if(senderPlatformId == null) {
                builder.addBotText("""
                        未注册该平台留言者ID：%s
                        留言内容：%s""".formatted(sender, message.getMessage()));
                continue;
            }
            builder.addText(senderPlatformId,
                    senderPlatformId,
                    """
                    留言者：%s
                    留言内容：%s""".formatted(senderPlatformId, message.getMessage()));
        }
        return builder.sendByPartAsync(8);
    }
}
