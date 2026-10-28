package org.zexnocs.teanekocore.api_response.interfaces;

import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;
import org.zexnocs.teanekocore.api_response.exception.APIRequestAnnotationNotFoundException;
import org.zexnocs.teanekocore.api_response.exception.APIURLErrorException;

/**
 * API 响应服务接口
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IAPIResponseService {
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
