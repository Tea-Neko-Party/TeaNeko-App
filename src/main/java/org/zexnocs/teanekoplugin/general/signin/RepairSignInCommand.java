package org.zexnocs.teanekoplugin.general.signin;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseTaskConfig;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataService;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 补签指令
 *
 * @author zExNocs
 * @date 2026/03/16
 * @since 4.3.4
 */
@Description("补签喵！")
@Command(value = {"/reapir-sign-in", "/补签", "补签", "补签喵", "/补签喵"},
        permission = CommandPermission.ALL)
@RequiredArgsConstructor
public class RepairSignInCommand {
    /// 补签卡 type
    public static final String REPAIR_CARD_TYPE = "repair-card";

    /// 补签系统
    private final RepairSignInService repairSignInService;

    /// 用于获取补签卡
    private final IItemDataService iItemDataService;
    private final ITeaUserService iTeaUserService;
    private final IDatabaseService iDatabaseService;

    @DefaultCommand
    @Description("""
            直接补签，用法如下：
            /补签 <?天数> 补签的最多时间。
            如果没有天数，则会报告补签卡的数量。
            """)
    public void repair(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("0") int k) {
        var data = commandData.getRawData();
        var user = data.getUserData().getUuid();
        var card = getRepairCard(user);
        // 如果 k == 0，那么就报告该用户补签卡的个数
        if(k == 0) {
            data.getMessageSender()
                    .sendAtReplyMessage("""
                主人您有 %d 张补签卡喵！
                输入 /补签 <天数> 来进行补签！
                如果天数超过数量，则会使用数量进行补签""".formatted(card.getCount()));
            return;
        }
        // 如果补签卡为 0，则补签失败
        if(card.getCount() == 0) {
            data.getMessageSender().sendAtReplyMessage("主人您没有补签卡喵！");
            return;
        }
        // 根据补签卡数量确定实际数量
        var count = Math.min(card.getCount(), k);
        repairSignInService.repairSignIn(data.getMessageSender(),
                user, System.currentTimeMillis(), count, card);
    }

    @SubCommand(value = {"give"}, permission = CommandPermission.DEBUG)
    @Description("""
            给予补签卡。
            /补签 give <用户平台 ID> <要添加的数量>
            如果为 all，则表示给予全部用户""")
    public void give(CommandData<ITeaNekoMessageData> commandData, String platformId, int count) {
        var data = commandData.getRawData();
        List<UUID> users = new ArrayList<>();
        if(platformId.equals("all")) {
            users.addAll(iTeaUserService.getAll(data.getClient()));
        } else {
            var user = iTeaUserService.get(data.getClient(), platformId);
            if(user == null) {
                data.getMessageSender().sendReplyMessage("该用户并没有注册喵！");
                return;
            }
            users.add(user);
        }
        // 为所有的 user 添加补签卡
        IDatabaseTaskConfig config = new DatabaseTaskConfig(iDatabaseService, "添加补签卡");
        for(var user : users) {
            var dto = getRepairCard(user);
            config.merge(
                    dto.getDatabaseTaskConfig("添加补签卡")
                            .addCount(count)
            );
        }
        config.pushWithFuture()
                .thenAccept(r -> data.getMessageSender().sendReplyMessage("添加成功喵！"))
                .finish();
    }

    /**
     * 根据 user 获取补签卡 dto
     *
     * @param user 用户 UUID
     * @return 补签卡 dto
     */
    public IItemDataDTO<?> getRepairCard(UUID user) {
        return iItemDataService.get(SignInService.NAMESPACE, user, REPAIR_CARD_TYPE);
    }
}
