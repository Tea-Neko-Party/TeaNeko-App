package org.zexnocs.teanekoapp.response;

import org.zexnocs.teanekoapp.response.exception.ResponseEchoDuplicateException;
import org.zexnocs.teanekoapp.response.interfaces.IResponseService;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于接收从 client 的响应信息。
 * 该类不应该由外部直接调用，而应该通过 {@link org.zexnocs.teanekoapp.sender.interfaces.ISenderService} 来注册 future。
 *
 * @author zExNocs
 * @date 2026/02/23
 * @since 4.0.8
 */
@EventListener
public class ResponseService implements IResponseService {
    /// 用于存放 echo → responseData 的映射关系
    private final Map<String, Pair<UUID, ISendData<?>>> echoToSendDataMap = new ConcurrentHashMap<>();

    /// objectMapper，允许将单个对象转化成 list
    private final ObjectMapper objectMapper;
    private final ILogger logger;
    private final ITaskService iTaskService;

    public ResponseService(ILogger logger, ITaskService iTaskService) {
        objectMapper = JsonMapper.builder()
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .build();
        this.logger = logger;
        this.iTaskService = iTaskService;
    }

    /**
     * 注册一个 future 来处理客户端的响应信息。
     *
     * @param echo     用于标识发送信息的唯一键，通常是一个 UUID。
     * @param key      在 taskService 中注册的 key，通常与 echo 中的 key 保持一致。
     * @param sendData 要发送的数据
     * @throws ResponseEchoDuplicateException 如果echo已经存在于
     */
    @Override
    public void register(String echo, UUID key, ISendData<?> sendData) throws ResponseEchoDuplicateException {
        var existing = echoToSendDataMap.putIfAbsent(echo, IndependentPair.of(key, sendData));
        if(existing != null) {
            var existingData = existing.second();
            throw new ResponseEchoDuplicateException("""
                    Echo: '%s' 已经存在于注册表中，无法注册重复的 echo。
                    已存在的 SendData: %s
                    新注册的 SendData: %s""".formatted(echo, existingData.toSendString(), sendData.toSendString()));
        }
    }

    /**
     * 删除一个 echo 的注册信息，通常在 future 完成后调用。
     *
     * @param echo 要删除的 echo
     */
    @Override
    public void unregister(String echo) {
        echoToSendDataMap.remove(echo);
    }

    /**
     * 监听 ResponseEvent，并将响应信息传递给对应的 future。
     *
     * @see ResponseEvent
     * @see ResponseData
     * @param event 响应事件
     */
    @EventHandler(priority = Integer.MIN_VALUE)
    public void onResponseEvent(ResponseEvent event) {
        var responseData = event.getData();
        var echo = responseData.getEcho();
        var rawData = responseData.getRawData();
        var success = responseData.isSuccess();

        // 尝试从 echoToSendDataMap 中获取对应的 SendData，如果没有找到，则记录错误日志并返回
        var pair = echoToSendDataMap.get(echo);
        if(pair == null) {
            logger.errorWithReport(ResponseService.class.getSimpleName(), """
                    收到一个未注册的 echo 的响应信息，无法找到对应的 SendData:
                    success: %s
                    echo: %s
                    rawData: %s""".formatted(success, echo, rawData));
            return;
        }
        var key = pair.first();
        var sendData = pair.second();
        var responseType = sendData.getResponseType();
        Object parsedData = null;
        // 尝试将 rawData 解析成对应的对象，如果解析失败，则记录错误日志并返回
        if(responseType != null
            && !responseType.equals(Void.class)
            && rawData != null
            && !rawData.isBlank()) {
            // 如果 responseType 不为 null，则尝试解析 rawData 成对应的对象
            try {
                parsedData = objectMapper.readValue(rawData, responseType);
            } catch (JacksonException e) {
                iTaskService.forceCompleteExceptionally(key, e);
                logger.errorWithReport(ResponseService.class.getSimpleName(), """
                    解析响应信息失败，无法将 rawData 解析成 %s 类型的对象。
                    success: %s
                    echo: %s
                    rawData: %s""".formatted(responseType.getName(), success, echo, rawData), e);
                return;
            }
        }
        // 将 parsedData 传递给对应的 future。
        iTaskService.complete(key, new TaskResult<>(success, parsedData));
    }

}
