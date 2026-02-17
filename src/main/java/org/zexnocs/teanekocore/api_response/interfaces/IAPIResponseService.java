package org.zexnocs.teanekocore.api_response.interfaces;

import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;
import org.zexnocs.teanekocore.api_response.exception.APIRequestAnnotationNotFoundException;
import org.zexnocs.teanekocore.api_response.exception.APIURLErrorException;

import java.util.concurrent.CompletableFuture;

/**
 * API 响应服务接口
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public interface IAPIResponseService {
    /**
     * 默认使用缓存添加一个 API 请求任务
     * @param requestData 请求数据对象，必须实现 {@link IAPIRequestData} 接口
     * @param responseType 响应数据类型，必须实现 {@link IAPIResponseData} 接口
     * @param <REQ> 请求数据类型，必须实现 {@link IAPIRequestData} 接口
     * @param <RES> 响应数据类型，必须实现 {@link IAPIResponseData} 接口
     * @throws APIRequestAnnotationNotFoundException 如果请求数据类缺少 APIRequestData 注解
     * @throws APIURLErrorException 如果 APIRequestData 注解中的 URL 为空或格式错误
     */
    default  <REQ extends IAPIRequestData, RES extends IAPIResponseData> CompletableFuture<RES> addTask(REQ requestData, Class<RES> responseType)
            throws APIRequestAnnotationNotFoundException, APIURLErrorException {
        return addTask(requestData, responseType, false, false);
    }

    /**
     * 添加一个 API 请求任务
     * @param requestData 请求数据对象，必须实现 {@link IAPIRequestData} 接口
     * @param responseType 响应数据类型，必须实现 {@link IAPIResponseData} 接口
     * @param disableCache 是否将该数据缓存，防止重复请求。如果为 false，则会将该数据缓存到 responseCache 中，
     * @param skipCache 是否跳过缓存，直接请求 API。如果为 false，则会先检查 responseCache 中是否有缓存的响应数据，
     * @param <REQ> 请求数据类型，必须实现 {@link IAPIRequestData} 接口
     * @param <RES> 响应数据类型，必须实现 {@link IAPIResponseData} 接口
     * @throws APIRequestAnnotationNotFoundException 如果请求数据类缺少 APIRequestData 注解
     * @throws APIURLErrorException 如果 APIRequestData 注解中的 URL 为空或格式错误
     */
    <REQ extends IAPIRequestData, RES extends IAPIResponseData> CompletableFuture<RES> addTask(
            REQ requestData,
            Class<RES> responseType,
            boolean disableCache,
            boolean skipCache
    ) throws APIRequestAnnotationNotFoundException, APIURLErrorException;
}
