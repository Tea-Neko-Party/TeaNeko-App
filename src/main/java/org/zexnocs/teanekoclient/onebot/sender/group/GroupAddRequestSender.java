package org.zexnocs.teanekoclient.onebot.sender.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.send.params.group.GroupAddRequestSendParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 同意/拒绝加群请求发送器
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.12
 * @version 4.5.0-l
 */
@Component("GroupAddRequestSender")
@SuppressWarnings("rawtypes")
public class GroupAddRequestSender extends AbstractOnebotSender<GroupAddRequestSendParamsData, Map> {

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public GroupAddRequestSender(ISenderService senderService,
                                 OnebotClient client,
                                 @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senderService, client, mapper);
    }

    /**
     * 同意加群请求
     *
     * @param flag 请求标识
     */
    public void approve(String flag) {
        send(GroupAddRequestSendParamsData.builder()
                    .flag(flag)
                    .approve(true)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }

    /**
     * 拒绝加群请求
     *
     * @param flag   请求标识
     * @param reason 拒绝理由
     */
    public void reject(String flag, String reason) {
        send(GroupAddRequestSendParamsData.builder()
                    .flag(flag)
                    .approve(false)
                    .reason(reason)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }

    /**
     * 通过原平台发送链接受入群请求，校验最终回执后才完成。
     *
     * @param flag 原平台请求回执标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> approveAsync(String flag) {
        return sendAsync(GroupAddRequestSendParamsData.builder()
                    .flag(flag)
                    .approve(true)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }

    /**
     * 通过原平台发送链拒绝入群请求，保留原请求标识及拒绝原因。
     *
     * @param flag 原平台请求回执标识
     * @param reason 首次生效的终止原因
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> rejectAsync(String flag, String reason) {
        return sendAsync(GroupAddRequestSendParamsData.builder()
                    .flag(flag)
                    .approve(false)
                    .reason(reason)
                    .build(),
            Duration.ZERO,
            3,
            Duration.ofMillis(200));
    }
}
