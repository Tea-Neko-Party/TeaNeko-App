package org.zexnocs.teanekoplugin.general.affection;

import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IForwardMessageSenderBuilder;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekoplugin.general.affection.interfaces.IAffectionService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 好感度查询指令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Description("查询好感度。")
@Command(value = {
        "/affection",
        "/好感度", "好感度",
        "/查询好感", "查询好感",
        "/查询好感度", "查询好感度",
        "/好感度查询", "好感度查询",
        "/好感查询", "好感查询",
        "/好感", "好感"},
        permission = CommandPermission.ALL)
public class AffectionCommand {
    private final IAffectionService iAffectionService;
    private final ITeaUserService iTeaUserService;

    /**
     * 装配好感度业务与用户目录，用原用户身份查询资料并等待反馈发送。
     *
     * @param iAffectionService 本次装配使用的 {@code IAffectionService} 服务
     * @param iTeaUserService 本次装配使用的 {@code ITeaUserService} 服务
     */
    public AffectionCommand(IAffectionService iAffectionService,
                            ITeaUserService iTeaUserService) {
        this.iAffectionService = iAffectionService;
        this.iTeaUserService = iTeaUserService;
    }

    /**
     * 发送用户信息。
     *
     * @param targetId 用户号
     * @param commandData 含可信来源和操作者的命令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            查询指定用户对你以及你对指定用户的好感度。
            如果没有指定用户，则查询你对前 5 个人的好感度""")
    @DefaultCommand
    public CompletionStage<Void> getAffection(CommandData<ITeaNekoMessageData> commandData,
                             @DefaultValue("null") String targetId) {
        var data = commandData.getRawData();
        var senderUUID = data.getUserData().getUuid();
        var client = data.getClient();
        var forwardSender = data.getForwardMessageSender();
        if(targetId.equals("null")) {
            // 获取到前 5 个好感度最高的用户
            var affectionList = iAffectionService.getTopKAffectionTargets(senderUUID, 100);
            return sendAffectionDetail(affectionList, senderUUID, client, forwardSender, data, "对他好感度");
        } else {
            // 获取到目标用户的 UUID
            var targetUUID = iTeaUserService.get(client, targetId);
            if(targetUUID == null) {
                return data.getMessageSender()
                        .sendReplyMessageAsync("未找到用户 " + targetId);
            }
            long currentToTarget = iAffectionService.getAffection(senderUUID, targetUUID);
            long targetToCurrent = iAffectionService.getAffection(targetUUID, senderUUID);
            var builder = client.getTeaNekoToolbox().getMessageSenderTools().getMsgListBuilder()
                    .addAt(data.getUserData().getUserIdInPlatform())
                    .addText(" 对 ")
                    .addAt(targetId)
                    .addText(" 的好感度为 " + currentToTarget + "\n")
                    .addAt(targetId)
                    .addText(" 对 ")
                    .addAt(data.getUserData().getUserIdInPlatform())
                    .addText(" 的好感度为 " + targetToCurrent)
                    .build();
            return data.getMessageSender().addMessages(builder).sendAsync();
        }
    }

    /**
     * 查询其他用户对你的前 5 名好感度排名；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            查询其他用户对你的前 5 名好感度排名。""")
    @SubCommand({"for_me", "对我", "谁在意我"})
    public CompletionStage<Void> getAffectionForMe(CommandData<ITeaNekoMessageData> commandData) {
        var data = commandData.getRawData();
        var senderUUID = data.getUserData().getUuid();
        var client = data.getClient();
        var affectionList = iAffectionService.getTopKAffectionSenders(senderUUID, 100);
        var forwardSender = data.getForwardMessageSender();

        return sendAffectionDetail(affectionList, senderUUID, client, forwardSender, data, "对我好感度");
    }

    /**
     * 发送好感度详情。
     *
     * @param affectionList 用于本次处理的 {@code affectionList} 数据
     * @param senderUUID 用于本次处理的 {@code senderUUID} 数据
     * @param client 用于本次处理的 {@code client} 数据
     * @param forwardSender 由装配方提供的 {@code IForwardMessageSenderBuilder} 协作依赖
     * @param data 本次处理的输入数据
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    private CompletionStage<Void> sendAffectionDetail(List<Pair<UUID, Integer>> affectionList,
                                     UUID senderUUID,
                                     ITeaNekoClient client,
                                     IForwardMessageSenderBuilder forwardSender,
                                     ITeaNekoMessageData data,
                                     String prompt) {
        int record = 0;
        for(var pair: affectionList) {
            // 如果是自己，则跳过
            var targetUuid = pair.first();
            if(targetUuid.equals(senderUUID)) {
                continue;
            }
            var affectionValue = pair.second();
            // 根据 target id 获取到 平台 id
            var thisTargetId = iTeaUserService.getPlatformId(client, targetUuid);
            if(thisTargetId == null) {
                // 如果为 null，说明是跨平台，则直接跳过。
                continue;
            }
            var messageBuilder = client.getTeaNekoToolbox().getMessageSenderTools().getMsgListBuilder()
                    .addText("第 %d 名: ".formatted(++record))
                    .addContents(client
                            .getTeaNekoToolbox()
                            .getPlatformUserInfoConstructorSender()
                            .getSimpleInfo(thisTargetId))
                    .addText("\n%s: %d".formatted(prompt, affectionValue));
            forwardSender.addBotList(messageBuilder.build());
            if(record == 5) {
                break;
            }
        }
        // 如果没有记录，说明没有好感度数据
        if(record == 0) {
            return data.getMessageSender()
                    .sendReplyMessageAsync("暂时还没有哦");
        }
        return forwardSender.sendAsync();
    }
}
