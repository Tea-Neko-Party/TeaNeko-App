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
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupMsgResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.message.GetMessageSendParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import org.zexnocs.teanekoclient.onebot.utils.OnebotMessageDataConvertUtils;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 获取消息发送器
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@Component
public class OnebotGetMsgSender extends AbstractOnebotSender<GetMessageSendParamsData, GroupMsgResponseData>
        implements IGetMessageSender {

    private final ITeaUserService iTeaUserService;
    private final OnebotTeaNekoClient onebotTeaNekoClient;

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public OnebotGetMsgSender(ISenderService senderService,
                              OnebotClient client,
                              @Qualifier("onebotObjectMapper") ObjectMapper mapper,
                              ITeaUserService iTeaUserService,
                              OnebotTeaNekoClient onebotTeaNekoClient) {
        super(senderService, client, mapper);
        this.iTeaUserService = iTeaUserService;
        this.onebotTeaNekoClient = onebotTeaNekoClient;
    }

    /**
     * 获取消息
     *
     * @param token     发送器发送环境的标识符
     * @param messageId 消息ID
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link GroupMsgResponseData }>>>
     */
    public TaskFuture<ITaskResult<ITeaNekoMessageData>> getMsg(String token, String messageId) {
        return sendWithFuture(token, GetMessageSendParamsData.builder().messageId(Long.parseLong(messageId)).build(),
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
                    return new TaskResult<>(true,
                            TeaNekoMessageData.builder()
                                    .time(ChinaDateUtil.Instance.convertToChinaZonedDateTime(data.getTime() * 1000L))
                                    .messageId(messageId)
                                    .messages(data.getMessage())
                                    .messageType(messageType)
                                    .userData(TeaNekoUserData.builder()
                                            .uuid(pair.second())
                                            .userIdInPlatform(String.valueOf(senderData.getUserId()))
                                            .nickname(senderData.getNickname())
                                            .role(OnebotMessageDataConvertUtils.Instance.getCommandPermission(messageType, senderData))
                                            .groupId(String.valueOf(senderData.getGroupId()))
                                            .build())
                                    .client(onebotTeaNekoClient)
                                    .build());
                });
    }
}
