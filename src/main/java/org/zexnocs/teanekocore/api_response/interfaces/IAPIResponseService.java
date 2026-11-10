package org.zexnocs.teanekocore.api_response.interfaces;

import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;
import org.zexnocs.teanekocore.api_response.exception.APIRequestAnnotationNotFoundException;
import org.zexnocs.teanekocore.api_response.exception.APIURLErrorException;

import java.util.function.Supplier;

/**
 * API 响应服务接口
 * <br>2026/11/10：增加复用执行控制的 SSE 完整聚合入口。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.9.1-l
 */
public interface IAPIResponseService {
    /**
     * 发送 JSON POST 并接收 SSE 数据；无缓存、无自动重试，只有正常 EOF 且聚合校验通过才返回结果。
     * @param requestData 可信连接与请求体
     * @param dataType 单个 SSE 事件的数据类型
     * @param accumulator 每次订阅独占的有界聚合器工厂
     * @param execution 覆盖整个接收阶段的时限及取消控制
     * @param <REQ> 请求类型
     * @param <DATA> 事件类型
     * @param <RES> 聚合结果类型
     * @return 完整响应任务
     * @throws UnsupportedOperationException 当实现尚未支持流式接收时
     */
    default <REQ extends IAPIRequestData, DATA extends IAPIResponseData, RES extends IAPIResponseData>
            TaskFuture<RES> addStreamTask(REQ requestData, Class<DATA> dataType,
            Supplier<? extends IAPIResponseAccumulator<DATA, RES>> accumulator, ExecutionPolicy execution) {
        throw new UnsupportedOperationException("HTTP 服务尚未支持流式响应聚合。");
    }

    /**
     * 显式运行时策略：阶段时限覆盖注解默认值，再受执行剩余时间限制。
     * 实现必须释放被取消的实际订阅；旧实现未适配时明确拒绝，不静默忽略策略。
     *
     * @param requestData 请求参数及可信连接覆盖信息
     * @param responseType 响应反序列化类型
     * @param disableCache 是否禁止本次响应写入缓存
     * @param skipCache 是否跳过缓存读取
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @param <REQ> 请求数据类型
     * @param <RES> 响应数据类型
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws APIRequestAnnotationNotFoundException 当该操作的校验或下游调用失败时
     * @throws APIURLErrorException 当该操作的校验或下游调用失败时
     * @throws UnsupportedOperationException 当该入口或适配器尚不支持请求的执行方式时
     */
    default <REQ extends IAPIRequestData, RES extends IAPIResponseData> TaskFuture<RES> addTask(
            REQ requestData, Class<RES> responseType, boolean disableCache, boolean skipCache,
            ExecutionPolicy execution) throws APIRequestAnnotationNotFoundException, APIURLErrorException {
        throw new UnsupportedOperationException("API response service does not support execution policies.");
    }

    /**
     * 默认使用缓存添加一个 API 请求任务
     *
     * @param requestData  请求数据对象，必须实现 {@link IAPIRequestData} 接口
     * @param responseType 响应数据类型，必须实现 {@link IAPIResponseData} 接口
     * @param <REQ>        请求数据类型，必须实现 {@link IAPIRequestData} 接口
     * @param <RES>        响应数据类型，必须实现 {@link IAPIResponseData} 接口
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws APIRequestAnnotationNotFoundException 如果请求数据类缺少 APIRequestData 注解
     * @throws APIURLErrorException                  如果 APIRequestData 注解中的 URL 为空或格式错误
     */
    default <REQ extends IAPIRequestData, RES extends IAPIResponseData> TaskFuture<RES> addTask(REQ requestData, Class<RES> responseType)
            throws APIRequestAnnotationNotFoundException, APIURLErrorException {
        return addTask(requestData, responseType, false, false);
    }

    /**
     * 添加一个 API 请求任务
     *
     * @param requestData  请求数据对象，必须实现 {@link IAPIRequestData} 接口
     * @param responseType 响应数据类型，必须实现 {@link IAPIResponseData} 接口
     * @param disableCache 是否将该数据缓存，防止重复请求。如果为 false，则会将该数据缓存到 responseCache 中，
     * @param skipCache    是否跳过缓存，直接请求 API。如果为 false，则会先检查 responseCache 中是否有缓存的响应数据，
     * @param <REQ>        请求数据类型，必须实现 {@link IAPIRequestData} 接口
     * @param <RES>        响应数据类型，必须实现 {@link IAPIResponseData} 接口
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws APIRequestAnnotationNotFoundException 如果请求数据类缺少 APIRequestData 注解
     * @throws APIURLErrorException                  如果 APIRequestData 注解中的 URL 为空或格式错误
     */
    <REQ extends IAPIRequestData, RES extends IAPIResponseData> TaskFuture<RES> addTask(
            REQ requestData,
            Class<RES> responseType,
            boolean disableCache,
            boolean skipCache
    ) throws APIRequestAnnotationNotFoundException, APIURLErrorException;
}
