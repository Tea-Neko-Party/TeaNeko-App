package org.zexnocs.teanekoplugin.onebot.request.review;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.configdata.api.default_config.LongDefaultConfigData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 管理群入群请求的指令。
 *
 * @author zExNocs
 * @date 2026/03/10
 * @since 4.1.4
 */
@ConfigManager(value = "group-request-review-default-id", description = """
        审核群设置默认被审核的群ID。
        配置：默认被审核的群ID。""",
        configType = LongDefaultConfigData.class,
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE})
@Description("管理群入群请求的指令。")
@Command(
        value = {"/gr"},
        scope = CommandScope.GROUP,
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class}
)
public class GroupRequestReviewCommand {
    private final IConfigDataService iConfigService;
    private final GroupRequestReviewRule groupRequestReviewRule;
    private final GroupRequestReviewService groupRequestReviewService;
    private final OnebotScopeIdUtils onebotScopeIdUtils;

    @Autowired
    public GroupRequestReviewCommand(GroupRequestReviewService groupRequestReviewService,
                                     GroupRequestReviewRule groupRequestReviewRule,
                                     IConfigDataService iConfigService, OnebotScopeIdUtils onebotScopeIdUtils) {
        this.groupRequestReviewService = groupRequestReviewService;
        this.groupRequestReviewRule = groupRequestReviewRule;
        this.iConfigService = iConfigService;
        this.onebotScopeIdUtils = onebotScopeIdUtils;
    }

    @Description("""
            显示指定群的所有入群请求。
            规格：/gr <群ID?>
            <群ID> 可选，默认查询主群。""")
    @DefaultCommand
    public void onDefault(CommandData<OnebotMessageData> commandData,
                          @DefaultValue("0") long groupId) {
        var data = commandData.getRawData();
        __doSomethingUsingConfig(data, groupId, finalGroupId -> {
            var messageListList = groupRequestReviewService.showAllDetailRequest(finalGroupId);
            data.getForwardMessageSender(CommandData.getCommandToken())
                    .addBotAllList(messageListList)
                    .sendByPart(8);
        });
    }

    @Description("""
            接受指定入群请求。
            规格：/gr 接受 <群ID?> <请求ID?>
            <群ID> 可选，默认查询主群。
            <请求ID> 可选，默认查询主群的第一个入群请求。
            优先使用 <群ID> 的默认""")
    @SubCommand({"accept", "接受"})
    public void onAccept(CommandData<OnebotMessageData> commandData,
                         @DefaultValue("0") long groupId,
                         @DefaultValue("0") long requestId) {
        var data = commandData.getRawData();
        var senderData = data.getUserData();
        __doSomethingUsingConfig(data, groupId, finalGroupId -> {
            // 如果 requestId 为 0，则使用上一次的 requestId
            long finalRequestId;
            if (requestId == 0) {
                finalRequestId = groupRequestReviewService.getLastRequestId(finalGroupId);
            } else {
                finalRequestId = requestId;
            }
            var messageList = groupRequestReviewService.accept(CommandData.getCommandToken(),
                    finalGroupId, finalRequestId, Long.parseLong(Objects.requireNonNull(senderData.getGroupId())),
                    Long.parseLong(senderData.getUserIdInPlatform()));
            if(messageList == null) {
                return;
            }
            data.getMessageSender(CommandData.getCommandToken())
                    .addMessages(messageList)
                    .send();
        });
    }

    @Description("""
            拒绝指定入群请求。
            规格：/gr 拒绝 <群ID?> <请求ID?>
            <群ID> 可选，默认查询主群。
            <请求ID> 可选，默认查询主群的第一个入群请求。
            优先使用 <群ID> 的默认""")
    @SubCommand({"reject", "拒绝"})
    public void onReject(CommandData<OnebotMessageData> commandData,
                         @DefaultValue("0") long groupId,
                         @DefaultValue("0") long requestId) {
        var data = commandData.getRawData();
        var senderData = data.getUserData();
        __doSomethingUsingConfig(data, groupId, finalGroupId -> {
            // 如果 requestId 为 0，则使用上一次的 requestId
            long finalRequestId;
            if (requestId == 0) {
                finalRequestId = groupRequestReviewService.getLastRequestId(finalGroupId);
            } else {
                finalRequestId = requestId;
            }
            var messageList = groupRequestReviewService.reject(CommandData.getCommandToken(),
                    finalGroupId, finalRequestId, Long.parseLong(Objects.requireNonNull(senderData.getGroupId())),
                    Long.parseLong(senderData.getUserIdInPlatform()));
            if(messageList == null) {
                return;
            }
            data.getMessageSender(CommandData.getCommandToken())
                    .addMessages(messageList)
                    .send();
        });
    }

    @Description("""
            查询一个入群请求的详细信息。
            规格：/gr info <群ID?> <请求ID?>
            <群ID> 可选，默认查询主群。
            <请求ID> 可选，默认查询主群的第一个入群请求。
            优先使用 <群ID> 的默认""")
    @SubCommand({"info", "详情"})
    public void onInfo(CommandData<OnebotMessageData> commandData,
                       @DefaultValue("0") long groupId,
                       @DefaultValue("0") long requestId) {
        var data = commandData.getRawData();
        __doSomethingUsingConfig(data, groupId, finalGroupId -> {
            // 如果 requestId 为 0，则使用上一次的 requestId
            long finalRequestId;
            if (requestId == 0) {
                finalRequestId = groupRequestReviewService.getLastRequestId(finalGroupId);
            } else {
                finalRequestId = requestId;
            }
            var messageList = groupRequestReviewService.showOneDetailRequest(finalGroupId, finalRequestId);
            data.getMessageSender(CommandData.getCommandToken())
                    .addMessages(messageList)
                    .send();
        });
    }

    /**
     * 执行一些基于配置的操作。
     * @param data 入群请求数据
     * @param reviewedGroupId 被审核的群ID
     * @param callback 回调函数，接受审核的群ID
     */
    private void __doSomethingUsingConfig(OnebotMessageData data,
                                          long reviewedGroupId,
                                          Consumer<Long> callback) {
        var currentGroupId = Long.parseLong(Objects.requireNonNull(data.getUserData().getGroupId()));
        if(reviewedGroupId == 0) {
            // 如果没有指定群ID，则尝试使用配置中的默认群ID
            iConfigService.getConfigData(this, LongDefaultConfigData.class,
                            onebotScopeIdUtils.getGroupConfigKey(currentGroupId))
                .ifPresentOrElse(config -> {
                    var configId = config.getValue();
                    if(configId == 0) {
                        data.getMessageSender(CommandData.getCommandToken())
                                .sendAtReplyMessage("没有配置默认被审核的群ID喵！");
                        return;
                    }
                    // 否则使用配置中的默认群ID
                    __doSomethingUsingConfig(data, configId, callback);
                }, () -> data.getMessageSender(CommandData.getCommandToken())
                        .sendAtReplyMessage("没有配置默认被审核的群ID喵！"));
            return;
        }

        // 判断当前审核群ID 是否在 被审核的群的审核列表中
        iConfigService.getConfigData(groupRequestReviewRule,
                        GroupRequestReviewRuleConfig.class,
                        onebotScopeIdUtils.getGroupConfigKey(reviewedGroupId))
            .ifPresentOrElse(config -> {
                        var list = config.getReviewGroupList();
                        if(list == null || list.isEmpty() || !list.contains(currentGroupId)) {
                            data.getMessageSender(CommandData.getCommandToken())
                                    .sendAtReplyMessage("当前群不在被审核的群的审核列表中喵！");
                            return;
                        }
                        callback.accept(reviewedGroupId);
                    },
                    () -> data.getMessageSender(CommandData.getCommandToken())
                            .sendAtReplyMessage("该群没有开启群申请审核喵！"));
    }
}
