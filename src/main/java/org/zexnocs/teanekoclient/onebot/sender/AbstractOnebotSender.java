package org.zexnocs.teanekoclient.onebot.sender;

import org.zexnocs.teanekoapp.sender.AbstractSender;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;
import org.zexnocs.teanekoclient.onebot.data.send.OnebotSendData;
import org.zexnocs.teanekoclient.onebot.event.sent.OnebotSentEvent;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * 符合 onebot 规范的发送器的基类，提供了发送器的基本功能和接口定义。
 *
 * @param <S> 发送数据参数的类型
 * @param <R> response 类型
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.12
 * @version 4.5.0-l
 * @see ISendParamsData
 * @see OnebotSendData
 */
public class AbstractOnebotSender<S extends ISendParamsData<R>, R> extends AbstractSender<OnebotSendData<S, R>, R> {

    /**
     * 等待平台请求结束；被取消后产生的空结果不能冒充操作成功。
     *
     * @param params 用于本次处理的 {@code params} 数据
     * @param delay 提交任务前的延迟
     * @param retries 用于本次处理的 {@code retries} 数据
     * @param retryDelay 用于本次处理的 {@code retryDelay} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public CompletionStage<Void> sendAsync(S params, Duration delay, int retries, Duration retryDelay) {
        return TaskStages.successful(sendWithFuture(params, delay, retries, retryDelay), "OneBot action")
                .thenAccept(responses -> Objects.requireNonNull(responses, "OneBot action returned no response"));
    }

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
     * @param sendParamsData 发送数据参数，包含了发送数据的具体内容和相关配置
     * @param delay          发送延迟
     * @param maxRetryCount  最大重试次数
     * @param retryDelay     重试间隔
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     */
    public TaskFuture<ITaskResult<List<R>>> sendWithFuture(S sendParamsData,
                                                           Duration delay,
                                                           int maxRetryCount,
                                                           Duration retryDelay) {
        return sendWithFuture(new OnebotSentEvent<>(buildSendData(sendParamsData)),
                delay,
                maxRetryCount,
                retryDelay);
    }

    /**
     * 使用
     * {@link ISendParamsData}
     * 发送数据
     *
     * @param sendParamsData 发送数据参数，包含了发送数据的具体内容和相关配置
     * @param delay          发送延迟
     * @param maxRetryCount  最大重试次数
     * @param retryDelay     重试间隔
     */
    public void send(S sendParamsData,
                     Duration delay,
                     int maxRetryCount,
                     Duration retryDelay) {
        sendWithFuture(sendParamsData, delay, maxRetryCount, retryDelay)
                .finish();
    }

    /**
     * 构造一个 {@link OnebotSendData} 对象
     *
     * @param sendParamsData 用于本次处理的 {@code sendParamsData} 数据
     * @return 满足本方法约束的 {@code OnebotSendData<S, R>} 结果
     */
    public OnebotSendData<S, R> buildSendData(S sendParamsData) {
        return new OnebotSendData<>(sendParamsData, client, mapper);
    }
}
