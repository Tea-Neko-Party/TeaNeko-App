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

/**
 * 好感度查询指令。
 *
 * @author zExNocs
 * @date 2026/03/06
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

    public AffectionCommand(IAffectionService iAffectionService,
                            ITeaUserService iTeaUserService) {
        this.iAffectionService = iAffectionService;
        this.iTeaUserService = iTeaUserService;
    }

    /**
     * 发送用户信息。
     *
     * @param targetId 用户号
     */
    @Description("""
            查询指定用户对你以及你对指定用户的好感度。
            如果没有指定用户，则查询你对前 5 个人的好感度""")
    @DefaultCommand
    public void getAffection(CommandData<ITeaNekoMessageData> commandData,
                             @DefaultValue("null") String targetId) {
        var data = commandData.getRawData();
        var senderUUID = data.getUserData().getUuid();
        var client = data.getClient();
        var forwardSender = data.getForwardMessageSender(CommandData.getCommandToken());
        if(targetId.equals("null")) {
            // 获取到前 5 个好感度最高的用户
            var affectionList = iAffectionService.getTopKAffectionTargets(senderUUID, 100);
            sendAffectionDetail(affectionList, senderUUID, client, forwardSender, data, "对他好感度");
        } else {
            // 获取到目标用户的 UUID
            var targetUUID = iTeaUserService.get(client, targetId);
            if(targetUUID == null) {
                data.getMessageSender(CommandData.getCommandToken())
                        .sendReplyMessage("未找到用户 " + targetId);
                return;
            }
            long currentToTarget = iAffectionService.getAffection(senderUUID, targetUUID);
            long targetToCurrent = iAffectionService.getAffection(targetUUID, senderUUID);
            var builder = client.getTeaNekoToolbox().getMessageSenderTools().getMsgListBuilder()
                    .addAtMessage(data.getUserData().getUserIdInPlatform())
                    .addTextMessage(" 对 ")
                    .addAtMessage(targetId)
                    .addTextMessage(" 的好感度为 " + currentToTarget + "\n")
                    .addAtMessage(targetId)
                    .addTextMessage(" 对 ")
                    .addAtMessage(data.getUserData().getUserIdInPlatform())
                    .addTextMessage(" 的好感度为 " + targetToCurrent)
                    .build();
            data.getMessageSender(CommandData.getCommandToken()).addMessages(builder).send();
        }
    }

    @Description("""
            查询其他用户对你的前 5 名好感度排名。""")
    @SubCommand({"for_me", "对我", "谁在意我"})
    public void getAffectionForMe(CommandData<ITeaNekoMessageData> commandData) {
        var data = commandData.getRawData();
        var senderUUID = data.getUserData().getUuid();
        var client = data.getClient();
        var affectionList = iAffectionService.getTopKAffectionSenders(senderUUID, 100);
        var forwardSender = data.getForwardMessageSender(CommandData.getCommandToken());

        sendAffectionDetail(affectionList, senderUUID, client, forwardSender, data, "对我好感度");
    }

    /**
     * 发送好感度详情。
     */
    private void sendAffectionDetail(List<Pair<UUID, Integer>> affectionList,
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
                    .addTextMessage("第 %d 名: ".formatted(++record))
                    .addMessages(client
                            .getTeaNekoToolbox()
                            .getPlatformUserInfoConstructorSender()
                            .getSimpleInfo(thisTargetId))
                    .addTextMessage("\n%s: %d".formatted(prompt, affectionValue));
            forwardSender.addBotList(messageBuilder.build());
            if(record == 5) {
                break;
            }
        }
        // 如果没有记录，说明没有好感度数据
        if(record == 0) {
            data.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage("暂时还没有哦");
            return;
        }
        forwardSender.send();
    }
}
