package org.zexnocs.teanekoplugin.onebot.title;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.sender.group.SetGroupSpecialTitleSender;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 设置 onebot 群内的头衔指令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.1
 * @version 4.5.0-l
 */
@Description("""
            设置群内的头衔。
            使用前提是机器人是群主。
            头衔里要带“猫、喵、🐱”。
            每日只能设置一次。
            可以叫管理员来帮你设置(无限制)
            样例：“/头衔 新人”，会获得“新人猫”的头衔。""")
@Command(value = {"/setTitle", "/st", "/头衔"},
        scope = CommandScope.GROUP,
        permission = CommandPermission.ALL,
        permissionPackage = "onebot.group.set-title",
        supportedClients = {OnebotTeaNekoClient.class})
public class SetSpecialTitleCommand {
    private final SetSpecialTitleService setGroupSpecialTitleService;
    // 用于强制设置头衔的发送器
    private final SetGroupSpecialTitleSender setGroupSpecialTitleSender;

    /**
     * 组合受限头衔业务与平台发送器，按原命令权限选择普通或 DEBUG 入口。
     *
     * @param setGroupSpecialTitleService 由装配方提供的 {@code SetSpecialTitleService} 协作依赖
     * @param setGroupSpecialTitleSender 由装配方提供的 {@code SetGroupSpecialTitleSender} 协作依赖
     */
    @Autowired
    public SetSpecialTitleCommand(SetSpecialTitleService setGroupSpecialTitleService,
                                  SetGroupSpecialTitleSender setGroupSpecialTitleSender) {
        this.setGroupSpecialTitleService = setGroupSpecialTitleService;
        this.setGroupSpecialTitleSender = setGroupSpecialTitleSender;
    }

    /**
     * 设置自己的头衔。每日只能设置一次；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param data 本次处理的输入数据
     * @param title 需要提交的群头衔文本
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            设置自己的头衔。每日只能设置一次。
            如果头衔里不带“猫、喵、🐱”，则会自动在后面加一个“猫”。
            头衔总长度不能超过 6 个字符。
            样例：“/头衔 新人”，会获得“新人猫”的头衔。""")
    @DefaultCommand
    public CompletionStage<Void> setGroupSpecialTitleWithLimit(CommandData<OnebotMessageData> data, String title) {
        var rawData = data.getRawData();
        var onebotData = rawData.getOnebotRawMessageData();
        var groupId = onebotData.getGroupId();
        var userId = onebotData.getUserId();
        return setGroupSpecialTitleService.setGroupSpecialTitleWithLimit(
                groupId, userId, title)
                .thenCompose(message -> message == null ? CompletableFuture.completedStage(null)
                        : rawData.getMessageSender().sendReplyMessageAsync(message));
    }

    /**
     * 可以帮别人无限制设置头衔；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param data 本次处理的输入数据
     * @param targetId 原业务规则允许操作的目标标识
     * @param title 需要提交的群头衔文本
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            群主指令。
            可以帮别人无限制设置头衔。
            长度不能超过 6 个字符。
            样例：“/头衔 给予 @xxx 新人”，会让 xxx 获得“新人”的头衔。""")
    @SubCommand(value = {"give", "给予"}, permission = CommandPermission.DEBUG)
    public CompletionStage<Void> setGroupSpecialTitleForOther(CommandData<OnebotMessageData> data, long targetId, String title) {
        var rawData = data.getRawData();
        var onebotData = rawData.getOnebotRawMessageData();
        var groupId = onebotData.getGroupId();
        return setGroupSpecialTitleService.setGroupSpecialTitle(
                groupId, targetId, title)
                .thenCompose(message -> message == null ? CompletableFuture.completedStage(null)
                        : rawData.getMessageSender().sendReplyMessageAsync(message));
    }

    /**
     * 重置某人可以今天再次设置头衔；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param data 本次处理的输入数据
     * @param targetId 原业务规则允许操作的目标标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            管理员指令。
            重置某人可以今天再次设置头衔。
            样例：“/头衔 重置 @xxx”，会让 xxx 今天可以再次设置头衔。""")
    @SubCommand(value = {"reset", "重置"}, permission = CommandPermission.ADMIN)
    public CompletionStage<Void> resetGroupSpecialTitleForOther(CommandData<OnebotMessageData> data, long targetId) {
        var rawData = data.getRawData();
        var onebotData = rawData.getOnebotRawMessageData();
        var groupId = onebotData.getGroupId();
        var message = setGroupSpecialTitleService.removeLimit(groupId, targetId);
        if (message != null) {
            return rawData.getMessageSender()
                    .sendReplyMessageAsync(message);
        }

        return CompletableFuture.completedStage(null);
    }

    /**
     * 强制设置头衔，无视六个字符的限制；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param data 本次处理的输入数据
     * @param targetId 原业务规则允许操作的目标标识
     * @param title 需要提交的群头衔文本
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            debugger指令。
            强制设置头衔，无视六个字符的限制。""")
    @SubCommand(value = {"debug"}, permission = CommandPermission.DEBUG)
    public CompletionStage<Void> setGroupSpecialTitleDebug(CommandData<OnebotMessageData> data, long targetId, String title) {
        var rawData = data.getRawData();
        var onebotData = rawData.getOnebotRawMessageData();
        var groupId = onebotData.getGroupId();
        return setGroupSpecialTitleSender.setGroupSpecialTitleAsync(groupId, targetId, title);
    }
}
