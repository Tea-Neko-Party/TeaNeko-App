package org.zexnocs.teanekoplugin.general.info;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.UUID;

/**
 * 查询用户在群组中的信息
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
@Description("查询用户在群组中的信息。")
@Command(value = {"/info", "info", "/信息"},
        permission = CommandPermission.ALL)
public class InfoCommand {
    private final InfoService infoService;
    private final ITeaUserService iTeaUserService;

    @Autowired
    public InfoCommand(InfoService infoService, ITeaUserService iTeaUserService) {
        this.infoService = infoService;
        this.iTeaUserService = iTeaUserService;
    }

    /**
     * 发送用户信息。
     *
     * @param userId 用户号
     */
    @Description("""
            查询用户的信息。
            格式：/info <用户?>
            默认用户为当前用户。""")
    @DefaultCommand
    public void sendGeneralInfo(CommandData<ITeaNekoMessageData> commandData,
                                @DefaultValue("null") String userId) {
        var data = commandData.getRawData();
        UUID uuid;
        if(userId.equals("null")) {
            uuid = data.getUserData().getUuid();
            userId = data.getUserData().getUserIdInPlatform();
        } else {
            uuid = iTeaUserService.get(data.getClient(), userId);
        }
        if(uuid == null) {
            data.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage("用户不存在喵！");
            return;
        }
        infoService.sentGeneralInfo(data, uuid, userId);
    }

    @Description("通过 UUID 查询用户信息。")
    @SubCommand(value = {"uuid"}, permission = CommandPermission.DEBUG)
    public void onGet(CommandData<ITeaNekoMessageData> commandData, String uuid) {
        var data = commandData.getRawData();
        String teaUserId;
        try {
            teaUserId = iTeaUserService.getPlatformId(data.getClient(), UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            data.getMessageSender(CommandData.getCommandToken())
                    .sendTextMessage("uuid 格式错误喵！");
            return;
        }
        var userInfo = data.getClient().getTeaNekoToolbox().getPlatformUserInfoConstructorSender();

        data.getMessageSender(CommandData.getCommandToken())
                .addReplyMessage()
                .addMessages(userInfo.getSimpleInfo(teaUserId))
                .send();
    }
}
