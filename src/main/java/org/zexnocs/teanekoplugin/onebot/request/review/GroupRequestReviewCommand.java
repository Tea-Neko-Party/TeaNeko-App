package org.zexnocs.teanekoplugin.onebot.request.review;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekoplugin.general.MainGroupConfigManager;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 管理群入群请求的指令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.4
 * @version 4.5.0-l
 */
@Command(
        value = {"/gr"},
        scope = CommandScope.GROUP,
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class}
)
@RequiredArgsConstructor
public class GroupRequestReviewCommand {
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
                    .thenCompose(messageList -> messageList == null ? CompletableFuture.completedStage(null)
                            : data.getMessageSender().addMessages(messageList).sendAsync());
        });
    }

    /**
     * 拒绝指定入群请求；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
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
                    .thenCompose(messageList -> messageList == null ? CompletableFuture.completedStage(null)
                            : data.getMessageSender().addMessages(messageList).sendAsync());
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
