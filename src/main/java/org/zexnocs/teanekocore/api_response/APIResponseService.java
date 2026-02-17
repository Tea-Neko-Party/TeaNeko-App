package org.zexnocs.teanekocore.api_response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import org.zexnocs.teanekocore.api_response.api.APIRequestData;
import org.zexnocs.teanekocore.api_response.api.APIRequestParam;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;
import org.zexnocs.teanekocore.api_response.exception.APIRequestAnnotationNotFoundException;
import org.zexnocs.teanekocore.api_response.exception.APIURLErrorException;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.util.retry.Retry;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * APIResponseService 是一个用于处理 API 请求和响应的服务类。
 * 它使用 Spring WebClient 来发送 HTTP 请求，并使用 Reactor 来处理异步响应。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
@Service
public class APIResponseService implements IAPIResponseService {
    /// webClient 缓存 url -> webClient 实例
    private final Map<String, WebClient> webClientCache = new ConcurrentHashMap<>();

    /// 响应缓存，缓解重复请求问题
    private final ConcurrentMapCacheContainer<String, ResponseCache<?>> responseCache;

    /// 用于执行 API 请求的 Scheduler
    private final Scheduler scheduler;

    @Autowired
    public APIResponseService(Scheduler apiScheduler, ICacheService iCacheService) {
        this.scheduler = apiScheduler;
        this.responseCache = ConcurrentMapCacheContainer.of(iCacheService);
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
    @Override
    public <REQ extends IAPIRequestData, RES extends IAPIResponseData> CompletableFuture<RES> addTask(
            REQ requestData,
            Class<RES> responseType,
            boolean disableCache,
            boolean skipCache
    ) throws APIRequestAnnotationNotFoundException,
            APIURLErrorException {
        var apiRequestAnnotation = requestData.getClass().getAnnotation(APIRequestData.class);
        if (apiRequestAnnotation == null) {
            throw new APIRequestAnnotationNotFoundException("类 " + requestData.getClass().getName() + " 缺少 APIRequestData 注解");
        }
        var url = apiRequestAnnotation.baseUrl();
        // 检测 URL 是否正确
        if (url == null || url.isBlank()) {
            throw new APIURLErrorException("APIRequestData 注解中的 URL 不能为空");
        }
        // 获取 WebClient 实例
        var future = new CompletableFuture<RES>();
        var webClient = _getWebClient(url);
        var params = _extractParams(requestData);
        boolean isPost = apiRequestAnnotation.method().equalsIgnoreCase("POST");
        String cacheKey = url + "?" + params.hashCode() + "@" + responseType.getName();

        // 如果不跳过缓存，则尝试从缓存中获取响应
        if(!skipCache) {
            var cachedResponse = responseCache.get(cacheKey);
            if (cachedResponse != null && cachedResponse.responseType.equals(responseType)) {
                // 如果缓存中有响应，直接调用成功回调
                future.complete(responseType.cast(cachedResponse.responseData()));
                return future;
            }
        }

        Mono<RES> mono;
        if(isPost) {
            mono = _getMonoForPost(webClient, apiRequestAnnotation, requestData, params, responseType);
        } else {
            mono = _getMonoForGet(webClient, apiRequestAnnotation, requestData, params, responseType);
        }
        // 订阅 Mono 对象
        mono.timeout(Duration.ofMillis(apiRequestAnnotation.timeoutInMillis()))
            .retryWhen(Retry.fixedDelay(apiRequestAnnotation.retryCount(),
                    Duration.ofMillis(apiRequestAnnotation.retryDelayInMillis())))
            .subscribeOn(scheduler)
            .doOnNext(res -> {
                // 如果没有禁用缓存且响应类型匹配，则将响应数据存入缓存
                if(!disableCache) {
                    responseCache.put(cacheKey, new ResponseCache<>(responseType, res));
                }
            })
            .subscribe(future::complete, future::completeExceptionally);
        return future;
    }

    /**
     * 从缓存中获取 webClient
     * @param url API 的基础 URL
     * @return WebClient 实例
     */
    private WebClient _getWebClient(String url) {
        return webClientCache.computeIfAbsent(url,
                key -> WebClient.builder()
                        .baseUrl(key)
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .build());
    }

    /**
     * 获取 Mono 对象用于 GET 请求
     */
    private <RES extends IAPIResponseData> Mono<RES> _getMonoForGet(
            WebClient webClient, APIRequestData apiRequestAnnotation,
            IAPIRequestData requestData, Map<String, Object> params,
            Class<RES> responseType) {
        return webClient.get()
                .uri(uriBuilder -> _buildUri(uriBuilder, apiRequestAnnotation.path(), params))
                .headers(h -> requestData.headers().forEach(h::add))
                .retrieve()
                .bodyToMono(responseType);
    }

    /**
     * 获取 Mono 对象用于 POST 请求
     */
    private <RES extends IAPIResponseData> Mono<RES> _getMonoForPost(
            WebClient webClient, APIRequestData apiRequestAnnotation,
            IAPIRequestData requestData, Map<String, Object> params,
            Class<RES> responseType) {
        boolean isJson = apiRequestAnnotation.isJson();
        if(isJson) {
            return webClient.post()
                    .uri(apiRequestAnnotation.path())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> requestData.headers().forEach(h::add))
                    .bodyValue(requestData)
                    .retrieve()
                    .bodyToMono(responseType);
        }
        return webClient.post()
                .uri(apiRequestAnnotation.path())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(h -> requestData.headers().forEach(h::add))
                .body(BodyInserters.fromFormData(_toMultiValueMap(params)))
                .retrieve()
                .bodyToMono(responseType);
    }

    /**
     * 从 IAPIRequestData 对象中提取参数
     * @param obj 请求数据对象
     * @return 参数映射
     */
    private static Map<String, Object> _extractParams(IAPIRequestData obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        for(Field field: obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            // 获取 APIRequestParam 注解，如果没有则使用字段名作为键
            String key;
            var annotation = field.getAnnotation(APIRequestParam.class);
            if (annotation == null || annotation.value().isBlank()) {
                key = field.getName().toLowerCase();
            } else {
                key = annotation.value();
            }
            // 添加到映射中
            try {
                var val = field.get(obj);
                if(val != null) {
                    map.put(key, val);
                } else if (annotation != null) {
                    // 尝试使用注解中的默认值
                    String defaultValueInString = annotation.defaultValue();
                    if(defaultValueInString == null || defaultValueInString.isBlank()) {
                        continue;
                    }
                    var type = field.getType();
                    // 将默认值转换为字段类型，如果抛出异常则忽略
                    if (type.equals(int.class) || type.equals(Integer.class)) {
                        map.put(key, Integer.parseInt(defaultValueInString));
                    } else if (type.equals(boolean.class) || type.equals(Boolean.class)) {
                        map.put(key, Boolean.parseBoolean(defaultValueInString));
                    } else {
                        map.put(key, type.cast(defaultValueInString));
                    }
                }
            } catch (IllegalAccessException | ClassCastException ignored) {}
        }
        return map;
    }

    /**
     * 构造 URI
     */
    private static URI _buildUri(UriBuilder builder, String path, Map<String, Object> params) {
        var b = builder.path(path);
        params.forEach(b::queryParam);
        return b.build();
    }

    /**
     * 构造 MultiValueMap 用于 POST 请求
     * @param map 参数映射
     * @return MultiValueMap<String, String>
     */
    private static MultiValueMap<String, String> _toMultiValueMap(Map<String, Object> map) {
        MultiValueMap<String, String> m = new LinkedMultiValueMap<>();
        map.forEach((k, v) -> m.add(k, v.toString()));
        return m;
    }

    /**
     * 响应缓存类，用于存储 API 响应，防止重复请求
     *
     * @param responseType 缓存的响应类型
     * @param responseData 缓存的响应数据
     */
    private record ResponseCache<RES extends IAPIResponseData>(Class<RES> responseType, RES responseData) {}
}
