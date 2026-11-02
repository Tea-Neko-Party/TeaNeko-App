package org.zexnocs.teanekoclient.onebot.sender.message;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.message.TeaNekoMessageData;
import org.zexnocs.teanekoapp.message.TeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetMessageSender;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.core.OnebotDebuggerService;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupMsgResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.GetMessageSendParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageDataConvertUtils;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;

/**
 * 获取消息发送器
 * <br>2026/11/02：保留消息 scope 并优先使用顶层群号，供引用及平台工具校验来源。
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 * @version 4.6.0-l
 */
@Component
public class OnebotGetMsgSender extends AbstractOnebotSender<GetMessageSendParamsData, GroupMsgResponseData>
        implements IGetMessageSender {

    private final ITeaUserService iTeaUserService;
    private final OnebotTeaNekoClient onebotTeaNekoClient;
    private final OnebotDebuggerService onebotDebuggerService;
    // 2026/11/02：查询消息与原生消息共用既有 scope 构造规则。
    private final org.zexnocs.teanekoapp.command.TeaNekoCommandConverter converter;

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     * @param iTeaUserService TeaNeko 用户身份服务
     * @param onebotTeaNekoClient 平台客户端
     * @param onebotDebuggerService 原生权限解析
     * @param converter 已有作用域构造器
     */
    @Autowired
    public OnebotGetMsgSender(ISenderService senderService,
                              OnebotClient client,
                              @Qualifier("onebotObjectMapper") ObjectMapper mapper,
                              ITeaUserService iTeaUserService,
                              OnebotTeaNekoClient onebotTeaNekoClient,
                              OnebotDebuggerService onebotDebuggerService,
                              org.zexnocs.teanekoapp.command.TeaNekoCommandConverter converter) {
        super(senderService, client, mapper);
        this.iTeaUserService = iTeaUserService;
        this.onebotTeaNekoClient = onebotTeaNekoClient;
        this.onebotDebuggerService = onebotDebuggerService;
        this.converter = converter;
    }

    /**
     * 2026/11/02：获取完整平台消息及同原生入口规则构造的作用域，无法确认来源时返回失败。
     *
     * @param messageId 消息ID
     * @return 包含平台消息的实际任务结果，查询失败时结果明确标记不成功
     */
    public TaskFuture<ITaskResult<ITeaNekoMessageData>> getMsg(String messageId) {
        return sendWithFuture(GetMessageSendParamsData.builder().messageId(Long.parseLong(messageId)).build(),
                Duration.ZERO, 3, Duration.ofMillis(200))
                // 转化成 result 和 uuid
                .thenComposeTask(result -> {
                    if(!result.isSuccess()) {
                        return null;
                    }
                    var data = result.getResult().getFirst();
                    return iTeaUserService.getOrCreate(onebotTeaNekoClient,
                            String.valueOf(data.getSender().getUserId()))
                            .thenApply(uuid -> IndependentPair.of(data, uuid));
                })
                .thenApply(pair -> {
                    if(pair == null) {
                        // 失败
                        return new TaskResult<>(false, null);
                    }
                    GroupMsgResponseData data = pair.first();
                    var senderData = data.getSender();
                    var messageType = switch(data.getMessageType()) {
                        case "private" -> TeaNekoMessageType.PRIVATE;
                        case "group" -> TeaNekoMessageType.GROUP;
                        default -> TeaNekoMessageType.OTHER;
                    };
                    // 2026/11/02：群号位于响应顶层；未知群来源拒绝，不构造没有 scope 的消息。
                    var groupId = data.getGroupId() != null ? data.getGroupId() : senderData.getGroupId();
                    if (messageType == TeaNekoMessageType.GROUP && (groupId == null || groupId <= 0)) { return new TaskResult<>(false, null); }
                    var user = TeaNekoUserData.builder().uuid(pair.second())
                            .userIdInPlatform(String.valueOf(senderData.getUserId()))
                            .nickname(senderData.getNickname() == null ? "user" : senderData.getNickname())
                            .role(OnebotMessageDataConvertUtils.Instance.getCommandPermission(messageType, senderData, onebotDebuggerService))
                            .groupId(messageType == TeaNekoMessageType.GROUP ? String.valueOf(groupId) : null).build();
                    return new TaskResult<>(true, TeaNekoMessageData.builder()
                                    .time(Instant.ofEpochSecond(data.getTime()))
                                    .messageId(messageId)
                                    .messages(data.getMessage())
                                    .messageType(messageType)
                                    .userData(user)
                                    .scopeId(converter.getScopeId(messageType, user, onebotTeaNekoClient))
                                    .client(onebotTeaNekoClient)
                                    .build());
                });
    }
}
