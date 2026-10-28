package org.zexnocs.teanekoplugin.general.info;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 查询用户在群组中的信息
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Description("查询用户在群组中的信息。")
@Command(value = {"/info", "info", "/信息"},
        permission = CommandPermission.ALL)
public class InfoCommand {
    private final InfoService infoService;
    private final ITeaUserService iTeaUserService;

    /**
     * 注入资料展示与用户定位服务，目标资料由原业务入口读取。
     *
     * @param infoService 由装配方提供的 {@code InfoService} 协作依赖
     * @param iTeaUserService 本次装配使用的 {@code ITeaUserService} 服务
     */
    @Autowired
    public InfoCommand(InfoService infoService, ITeaUserService iTeaUserService) {
        this.infoService = infoService;
        this.iTeaUserService = iTeaUserService;
    }

    /**
     * 发送用户信息。
     *
     * @param userId 用户号
     * @param commandData 含可信来源和操作者的命令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            查询用户的信息。
            格式：/info <用户?>
            默认用户为当前用户。""")
    @DefaultCommand
    public CompletionStage<Void> sendGeneralInfo(CommandData<ITeaNekoMessageData> commandData,
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
            return data.getMessageSender()
                    .sendReplyMessageAsync("用户不存在喵！");
        }
        return infoService.sentGeneralInfo(data, uuid, userId);
    }

    /**
     * 通过 UUID 查询用户信息；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param uuid 用于本次处理的 {@code uuid} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("通过 UUID 查询用户信息。")
    @SubCommand(value = {"uuid"}, permission = CommandPermission.DEBUG)
    public CompletionStage<Void> onGet(CommandData<ITeaNekoMessageData> commandData, String uuid) {
        var data = commandData.getRawData();
        String teaUserId;
        try {
            teaUserId = iTeaUserService.getPlatformId(data.getClient(), UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return data.getMessageSender()
                    .sendTextMessageAsync("uuid 格式错误喵！");
        }
        var userInfo = data.getClient().getTeaNekoToolbox().getPlatformUserInfoConstructorSender();

        return data.getMessageSender()
                .addReplyMessage()
                .addMessages(userInfo.getSimpleInfo(teaUserId))
                .sendAsync();
    }
}
