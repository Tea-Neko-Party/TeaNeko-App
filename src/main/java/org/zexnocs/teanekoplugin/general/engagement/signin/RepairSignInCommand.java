package org.zexnocs.teanekoplugin.general.engagement.signin;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 补签指令
 *
 * <br>2026/11/04：增加中文指令显示名，供控制台目录与选择卡片使用。
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * <br>2026/11/07：异步副作用前复核固定插件代次，停用后拒绝迟到业务。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.3.4
 * @version 4.9.0-l
 */
@Description("补签喵！")
// 2026/11/04：显示名与稳定指令前缀分别维护。
@Command(displayName = "签到补签", value = {"/reapir-sign-in", "/补签", "补签", "补签喵", "/补签喵"},
        permission = CommandPermission.ALL)
@RequiredArgsConstructor
@PluginOwned("general.engagement")
public class RepairSignInCommand {
    // 2026/11/07：异步业务固定入口代次，停用后不启动后续副作用。
    private final PluginAccess plugins;
    /// 补签卡 type
    public static final String REPAIR_CARD_TYPE = "repair-card";

    /// 补签系统
    private final RepairSignInService repairSignInService;

    /// 用于获取补签卡
    private final IItemDataService iItemDataService;
    private final ITeaUserService iTeaUserService;
    private final IDatabaseService iDatabaseService;

    /**
     * 直接补签，用法如下：；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param k 用于本次处理的 {@code k} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @DefaultCommand
    @Description("""
            直接补签，用法如下：
            /补签 <?天数> 补签的最多时间。
            如果没有天数，则会报告补签卡的数量。
            """)
    public CompletionStage<Void> repair(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("0") int k) {
        var data = commandData.getRawData();
        var user = data.getUserData().getUuid();
        var card = getRepairCard(user);
        // 如果 k == 0，那么就报告该用户补签卡的个数
        if(k == 0) {
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("""

                主人您有 %d 张补签卡喵！
                输入 /补签 <天数> 来进行补签！
                如果天数超过数量，则会使用补签卡最大数量进行补签""".formatted(card.getCount()));
        }
        // 如果补签卡为 0，则补签失败
        if(card.getCount() == 0) {
            return data.getMessageSender().sendAtReplyMessageAsync("主人您没有补签卡喵！");
        }
        // 根据补签卡数量确定实际数量
        var count = Math.min(card.getCount(), k);
        return repairSignInService.repairSignIn(data.getMessageSender(),
                user, Instant.now(), count, card);
    }

    /**
     * 给予补签卡；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param platformId 目标用户的平台标识
     * @param count 用于本次处理的 {@code count} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @SubCommand(value = {"give"}, permission = CommandPermission.DEBUG)
    @Description("""
            给予补签卡。
            /补签 give <用户平台 ID> <要添加的数量>
            如果为 all，则表示给予全部用户""")
    public CompletionStage<Void> give(CommandData<ITeaNekoMessageData> commandData, String platformId, int count) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("general.engagement");
        var data = commandData.getRawData();
        List<UUID> users = new ArrayList<>();
        if(platformId.equals("all")) {
            users.addAll(iTeaUserService.getAll(data.getClient()));
        } else {
            var user = iTeaUserService.get(data.getClient(), platformId);
            if(user == null) {
                return data.getMessageSender().sendReplyMessageAsync("该用户并没有注册喵！");
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
        return config.addTransactionTask(() -> plugins.check(pluginToken)).pushAsync()
                .thenCompose(ignored -> {
                    plugins.check(pluginToken);
                    return data.getMessageSender().sendReplyMessageAsync("添加成功喵！");
                });
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
