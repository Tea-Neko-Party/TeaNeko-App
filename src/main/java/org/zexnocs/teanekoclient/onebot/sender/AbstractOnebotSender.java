package org.zexnocs.teanekoclient.onebot.sender;

import org.zexnocs.teanekoapp.sender.AbstractSender;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;
import org.zexnocs.teanekoclient.onebot.data.send.OnebotSendData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 符合 onebot 规范的发送器的基类，提供了发送器的基本功能和接口定义。
 *
 * @param <S> 发送数据参数的类型
 * @param <R> response 类型
 * @see ISendParamsData
 * @see OnebotSendData
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
public class AbstractOnebotSender<S extends ISendParamsData<R>, R> extends AbstractSender<OnebotSendData<S, R>, R> {

    /// 客户端
    private final OnebotClient client;

    /// mapper
    private final ObjectMapper mapper;

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    public AbstractOnebotSender(ISenderService senderService,
                                OnebotClient client,
                                ObjectMapper mapper) {
        super(senderService);
        this.client = client;
        this.mapper = mapper;
    }

    /**
     * 使用
     * {@link ISendParamsData}
     * 发送数据，并返回一个
     * {@link TaskFuture}
     * ，该任务将在指定的延迟后执行，并返回一个包含响应数据列表的任务结果。
     *
     * @param token 发送器发送环境的标识符
     * @param sendParamsData 发送数据参数，包含了发送数据的具体内容和相关配置
     * @param delay 发送延迟
     * @param maxRetryCount 最大重试次数
     * @param retryDelay 重试间隔
     * @return {@link TaskFuture }<{@link ITaskResult }<{@link List }<{@link R }>>>
     */
    public TaskFuture<ITaskResult<List<R>>> sendWithFuture(String token,
                                                           S sendParamsData,
                                                           Duration delay,
                                                           int maxRetryCount,
                                                           Duration retryDelay) {
        return sendWithFuture(new OnebotSendData<>(sendParamsData, client, mapper, token),
                delay,
                maxRetryCount,
                retryDelay);
    }

    /**
     * 使用
     * {@link ISendParamsData}
     * 发送数据
     *
     * @param token 发送器发送环境的标识符
     * @param sendParamsData 发送数据参数，包含了发送数据的具体内容和相关配置
     * @param delay 发送延迟
     * @param maxRetryCount 最大重试次数
     * @param retryDelay 重试间隔
     */
    public void send(String token,
                     S sendParamsData,
                     Duration delay,
                     int maxRetryCount,
                     Duration retryDelay) {
        sendWithFuture(token, sendParamsData, delay, maxRetryCount, retryDelay)
                .finish();
    }
}
