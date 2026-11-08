package org.zexnocs.teanekoplugin.onebot.requests.review;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekoplugin.general.group_management.MainGroupConfigManager;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 管理群入群请求的指令。
 *
 * <br>2026/11/04：增加中文指令显示名，供控制台目录与选择卡片使用。
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * <br>2026/11/07：异步副作用前复核固定插件代次，停用后拒绝迟到业务。
 * <br>2026/11/08：审核结果无需反馈时显式返回 Void 阶段，统一接受和拒绝命令的异步类型。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.4
 * @version 4.9.0-l
 */
// 2026/11/04：显示名与稳定指令前缀分别维护。
@Command(displayName = "入群申请审核",
        value = {"/gr"},
        scope = CommandScope.GROUP,
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class}
)
@RequiredArgsConstructor
@PluginOwned("onebot.requests")
public class GroupRequestReviewCommand {
    // 2026/11/07：异步业务固定入口代次，停用后不启动后续副作用。
    private final PluginAccess plugins;
    private final GroupRequestReviewRule groupRequestReviewRule;
    private final GroupRequestReviewService groupRequestReviewService;
    private final OnebotScopeIdUtils onebotScopeIdUtils;
    private final MainGroupConfigManager mainGroupConfigManager;
    private final IConfigDataService iConfigDataService;

    /**
     * 显示指定群的所有入群请求；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param groupId 目标群组标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            显示指定群的所有入群请求。
            规格：/gr <群ID?>
            <群ID> 可选，默认查询主群。""")
    @DefaultCommand
    public CompletionStage<Void> onDefault(CommandData<OnebotMessageData> commandData,
                          @DefaultValue("0") long groupId) {
        var data = commandData.getRawData();
        return __doSomethingUsingConfig(data, groupId, finalGroupId -> {
            var messageListList = groupRequestReviewService.showAllDetailRequest(finalGroupId);
            return data.getForwardMessageSender()
                    .addBotAllList(messageListList)
                    .sendByPartAsync(8);
        });
    }

    /**
     * 接受指定入群请求；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     * <br>2026/11/08：空反馈分支明确使用 Void，保持组合阶段与命令完成契约一致。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param groupId 目标群组标识
     * @param requestId 由宿主生成的请求标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    @Description("""
            接受指定入群请求。
            规格：/gr 接受 <群ID?> <请求ID?>
            <群ID> 可选，默认查询主群。
            <请求ID> 可选，默认查询主群的第一个入群请求。
            优先使用 <群ID> 的默认""")
    @SubCommand({"accept", "接受"})
    public CompletionStage<Void> onAccept(CommandData<OnebotMessageData> commandData,
                         @DefaultValue("0") long groupId,
                         @DefaultValue("0") long requestId) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("onebot.requests");
        var data = commandData.getRawData();
        var senderData = data.getUserData();
        return __doSomethingUsingConfig(data, groupId, finalGroupId -> {
            // 如果 requestId 为 0，则使用上一次的 requestId
            long finalRequestId;
            if (requestId == 0) {
                finalRequestId = groupRequestReviewService.getLastRequestId(finalGroupId);
            } else {
                finalRequestId = requestId;
            }
            return Objects.requireNonNull(groupRequestReviewService.accept(
                            finalGroupId, finalRequestId, Long.parseLong(Objects.requireNonNull(senderData.getGroupId())),
                            Long.parseLong(senderData.getUserIdInPlatform())))
                    .thenCompose(messageList -> {
                        plugins.check(pluginToken);
                        return messageList == null ? CompletableFuture.<Void>completedStage(null)
                                : data.getMessageSender().addMessages(messageList).sendAsync();
                    });
        });
    }

    /**
     * 拒绝指定入群请求；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     * <br>2026/11/08：空反馈分支明确使用 Void，保持组合阶段与命令完成契约一致。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param groupId 目标群组标识
     * @param requestId 由宿主生成的请求标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    @Description("""
            拒绝指定入群请求。
            规格：/gr 拒绝 <群ID?> <请求ID?>
            <群ID> 可选，默认查询主群。
            <请求ID> 可选，默认查询主群的第一个入群请求。
            优先使用 <群ID> 的默认""")
    @SubCommand({"reject", "拒绝"})
    public CompletionStage<Void> onReject(CommandData<OnebotMessageData> commandData,
                         @DefaultValue("0") long groupId,
                         @DefaultValue("0") long requestId) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("onebot.requests");
        var data = commandData.getRawData();
        var senderData = data.getUserData();
        return __doSomethingUsingConfig(data, groupId, finalGroupId -> {
            // 如果 requestId 为 0，则使用上一次的 requestId
            long finalRequestId;
            if (requestId == 0) {
                finalRequestId = groupRequestReviewService.getLastRequestId(finalGroupId);
            } else {
                finalRequestId = requestId;
            }
            return Objects.requireNonNull(groupRequestReviewService.reject(
                            finalGroupId, finalRequestId, Long.parseLong(Objects.requireNonNull(senderData.getGroupId())),
                            Long.parseLong(senderData.getUserIdInPlatform())))
                    .thenCompose(messageList -> {
                        plugins.check(pluginToken);
                        return messageList == null ? CompletableFuture.<Void>completedStage(null)
                                : data.getMessageSender().addMessages(messageList).sendAsync();
                    });
        });
    }

    /**
     * 查询一个入群请求的详细信息；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param groupId 目标群组标识
     * @param requestId 由宿主生成的请求标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            查询一个入群请求的详细信息。
            规格：/gr info <群ID?> <请求ID?>
            <群ID> 可选，默认查询主群。
            <请求ID> 可选，默认查询主群的第一个入群请求。
            优先使用 <群ID> 的默认""")
    @SubCommand({"info", "详情"})
    public CompletionStage<Void> onInfo(CommandData<OnebotMessageData> commandData,
                       @DefaultValue("0") long groupId,
                       @DefaultValue("0") long requestId) {
        var data = commandData.getRawData();
        return __doSomethingUsingConfig(data, groupId, finalGroupId -> {
            // 如果 requestId 为 0，则使用上一次的 requestId
            long finalRequestId;
            if (requestId == 0) {
                finalRequestId = groupRequestReviewService.getLastRequestId(finalGroupId);
            } else {
                finalRequestId = requestId;
            }
            var messageList = groupRequestReviewService.showOneDetailRequest(finalGroupId, finalRequestId);
            return data.getMessageSender()
                    .addMessages(messageList)
                    .sendAsync();
        });
    }

    /**
     * 执行一些基于配置的操作。
     *
     * @param data 入群请求数据
     * @param reviewedGroupId 被审核的群ID
     * @param callback 回调函数，接受审核的群ID
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    private CompletionStage<Void> __doSomethingUsingConfig(OnebotMessageData data,
                                          long reviewedGroupId,
                                          java.util.function.LongFunction<CompletionStage<Void>> callback) {
        long currentGroupId = Long.parseLong(Objects.requireNonNull(data.getUserData().getGroupId()));
        if (reviewedGroupId == 0) {
            var main = mainGroupConfigManager.getConfig(onebotScopeIdUtils.getGroupConfigKey(currentGroupId)).orElse(null);
            if (main == null) return data.getMessageSender().sendAtReplyMessageAsync("没有配置默认被审核的群ID喵！");
            try {
                reviewedGroupId = Long.parseLong(main.getValue());
            } catch (NumberFormatException e) {
                return data.getMessageSender().sendAtReplyMessageAsync("配置中的默认群ID不合法喵！");
            }
            if (reviewedGroupId == 0) return data.getMessageSender().sendAtReplyMessageAsync("没有配置默认被审核的群ID喵！");
        }
        var config = iConfigDataService.getConfigData(groupRequestReviewRule,
                GroupRequestReviewRuleConfig.class, onebotScopeIdUtils.getGroupConfigKey(reviewedGroupId)).orElse(null);
        if (config == null) return data.getMessageSender().sendAtReplyMessageAsync("该群没有开启群申请审核喵！");
        if (config.getReviewGroupList() == null || !config.getReviewGroupList().contains(currentGroupId)) {
            return data.getMessageSender().sendAtReplyMessageAsync("当前群不在被审核的群的审核列表中喵！");
        }
        return callback.apply(reviewedGroupId);
    }
}
