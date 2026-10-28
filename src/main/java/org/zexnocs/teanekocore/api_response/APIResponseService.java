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
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.api.APIRequestData;
import org.zexnocs.teanekocore.api_response.api.APIRequestParam;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;
import org.zexnocs.teanekocore.api_response.exception.APIRequestAnnotationNotFoundException;
import org.zexnocs.teanekocore.api_response.exception.APIURLErrorException;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.logger.ILogger;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * APIResponseService 是一个用于处理 API 请求和响应的服务类。
 * 它使用 Spring WebClient 来发送 HTTP 请求，并使用 Reactor 来处理异步响应。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public class APIResponseService implements IAPIResponseService {
    /// webClient 缓存 url -> webClient 实例
    private final Map<String, WebClient> webClientCache = new ConcurrentHashMap<>();

    /// 响应缓存，缓解重复请求问题
    private final ConcurrentMapCacheContainer<String, ResponseCache<?>> responseCache;

    /// 用于执行 API 请求的 Scheduler
    private final Scheduler scheduler;
    private final ILogger logger;
    private final Scheduler timerScheduler;
    private final Function<String, WebClient> webClientFactory;

    /**
     * 注入 HTTP 执行、阶段计时、缓存和客户端工厂；调度器由装配方管理，不为单个请求创建线程池。
     *
     * @param apiScheduler HTTP 工作使用的借用调度器
     * @param iCacheService 缓存注册与生命周期服务
     * @param logger 项目日志接口
     */
    @Autowired
    public APIResponseService(Scheduler apiScheduler, ICacheService iCacheService, ILogger logger) {
        this(apiScheduler, Schedulers.parallel(), iCacheService, logger, url -> WebClient.builder()
                .baseUrl(url).defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build());
    }

    // 测试/内部装配可借用独立时钟与传输，既有 Spring 构造入口保持不变。
    /**
     * 注入 HTTP 执行、阶段计时、缓存和客户端工厂；调度器由装配方管理，不为单个请求创建线程池。
     *
     * @param apiScheduler HTTP 工作使用的借用调度器
     * @param timerScheduler 阶段计时使用的借用调度器
     * @param cacheService 缓存注册与生命周期服务
     * @param logger 项目日志接口
     * @param webClientFactory 按可信地址创建 HTTP 客户端的工厂
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    APIResponseService(Scheduler apiScheduler, Scheduler timerScheduler, ICacheService cacheService,
                       ILogger logger, Function<String, WebClient> webClientFactory) {
        this.scheduler = Objects.requireNonNull(apiScheduler);
        this.timerScheduler = Objects.requireNonNull(timerScheduler);
        this.responseCache = ConcurrentMapCacheContainer.of(cacheService);
        this.logger = logger;
        this.webClientFactory = Objects.requireNonNull(webClientFactory);
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
    @Override
    public <REQ extends IAPIRequestData, RES extends IAPIResponseData> TaskFuture<RES> addTask(
            REQ requestData,
            Class<RES> responseType,
            boolean disableCache,
            boolean skipCache
    ) throws APIRequestAnnotationNotFoundException,
            APIURLErrorException {
        return addTaskInternal(requestData, responseType, disableCache, skipCache, ExecutionPolicy.defaults(), false);
    }

    /**
     * 按显式策略建立单次 HTTP 任务，阶段时限覆盖排队和重试等待，取消向实际订阅传播。
     *
     * @param requestData 请求参数及可信连接覆盖信息
     * @param responseType 响应反序列化类型
     * @param disableCache 是否禁止本次响应写入缓存
     * @param skipCache 是否跳过缓存读取
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @param <REQ> 请求数据类型
     * @param <RES> 响应数据类型
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    @Override
    public <REQ extends IAPIRequestData, RES extends IAPIResponseData> TaskFuture<RES> addTask(
            REQ requestData, Class<RES> responseType, boolean disableCache, boolean skipCache,
            ExecutionPolicy execution) {
        return addTaskInternal(requestData, responseType, disableCache, skipCache,
                Objects.requireNonNull(execution, "execution"), true);
    }

    /**
     * 校验请求声明并建立单次请求生命周期；按策略约束排队、重试等待和传输，只有已接受响应可以写缓存。
     *
     * @param requestData 请求参数及可信连接覆盖信息
     * @param responseType 响应反序列化类型
     * @param disableCache 是否禁止本次响应写入缓存
     * @param skipCache 是否跳过缓存读取
     * @param policy 本阶段的时限与借用控制策略
     * @param managedTimeout 是否将排队及重试等待纳入同一阶段时限
     * @param <REQ> 请求数据类型
     * @param <RES> 响应数据类型
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws APIRequestAnnotationNotFoundException 当该操作的校验或下游调用失败时
     * @throws APIURLErrorException 当该操作的校验或下游调用失败时
     */
    private <REQ extends IAPIRequestData, RES extends IAPIResponseData> TaskFuture<RES> addTaskInternal(
            REQ requestData, Class<RES> responseType, boolean disableCache, boolean skipCache,
            ExecutionPolicy policy, boolean managedTimeout) {
        var apiRequestAnnotation = requestData.getClass().getAnnotation(APIRequestData.class);
        if (apiRequestAnnotation == null) {
            throw new APIRequestAnnotationNotFoundException("类 " + requestData.getClass().getName() + " 缺少 APIRequestData 注解");
        }
        var url = _firstNotBlank(requestData.getBaseUrlOverride(), apiRequestAnnotation.baseUrl());
        var path = _firstNotBlank(requestData.getPathOverride(), apiRequestAnnotation.path());
        var method = _firstNotBlank(requestData.getMethodOverride(), apiRequestAnnotation.method());
        if (path == null) {
            path = "";
        }
        if (method == null) {
            method = "GET";
        }
        // 检测 URL 是否正确
        if (url == null || url.isBlank()) {
            throw new APIURLErrorException("APIRequestData 注解中的 URL 不能为空");
        }
        var completion = new CompletableFuture<RES>();
        var future = new TaskFuture<>(logger, "订阅 api", completion);
        Duration timeout;
        try {
            timeout = policy.effectiveTimeout(Duration.ofMillis(apiRequestAnnotation.timeoutInMillis()));
        } catch (RuntimeException stopped) {
            completion.completeExceptionally(stopped);
            return future;
        }
        var execution = new APIRequestExecution<>(policy, timeout, timerScheduler, completion, managedTimeout);
        if (execution.isDone()) {
            return future;
        }
        try {
            var params = _extractParams(requestData);
            String cacheKey = url + path + "?" + params.hashCode() + "@" + responseType.getName();
            if (!skipCache) {
                var cachedResponse = responseCache.get(cacheKey);
                if (cachedResponse != null && cachedResponse.responseType.equals(responseType)) {
                    execution.succeed(responseType.cast(cachedResponse.responseData()), ignored -> {});
                    return future;
                }
            }
            final String requestPath = path;
            final boolean isPost = method.equalsIgnoreCase("POST");
            // 实际创建/发送留到订阅阶段；排队期间取消不会启动传输。
            Mono<RES> source = Mono.defer(() -> {
                policy.checkActive();
                if (execution.isDone()) {
                    return Mono.empty();
                }
                var webClient = _getWebClient(url);
                return isPost
                        ? _getMonoForPost(webClient, apiRequestAnnotation, requestData, params, responseType, requestPath)
                        : _getMonoForGet(webClient, requestData, params, responseType, requestPath);
            });
            if (!managedTimeout) {
                // 旧入口保留每次尝试的注解超时；显式策略入口限制包括排队/重试在内的整个阶段。
                source = source.timeout(timeout, timerScheduler);
            }
            if (apiRequestAnnotation.retryCount() > 0) {
                source = source.retryWhen(Retry.fixedDelay(apiRequestAnnotation.retryCount(),
                        Duration.ofMillis(apiRequestAnnotation.retryDelayInMillis())).scheduler(timerScheduler));
            }
            execution.subscribe(source, scheduler, res -> {
                if (!disableCache) {
                    responseCache.put(cacheKey, new ResponseCache<>(responseType, res));
                }
            });
        } catch (RuntimeException failure) {
            execution.fail(failure);
        }
        return future;
    }

    /**
     * 从缓存中获取 webClient
     *
     * @param url API 的基础 URL
     * @return WebClient 实例
     */
    private WebClient _getWebClient(String url) {
        return webClientCache.computeIfAbsent(url, webClientFactory);
    }

    /**
     * 获取 Mono 对象用于 GET 请求
     *
     * @param webClient 用于本次处理的 {@code webClient} 数据
     * @param requestData 请求参数及可信连接覆盖信息
     * @param params 用于本次处理的 {@code params} 数据
     * @param responseType 响应反序列化类型
     * @param path 用于本次处理的 {@code path} 数据
     * @param <RES> 响应数据类型
     * @return 满足本方法约束的 {@code Mono<RES>} 结果
     */
    private <RES extends IAPIResponseData> Mono<RES> _getMonoForGet(
            WebClient webClient,
            IAPIRequestData requestData, Map<String, Object> params,
            Class<RES> responseType, String path) {
        return webClient.get()
                .uri(uriBuilder -> _buildUri(uriBuilder, path, params))
                .headers(h -> requestData.headers().forEach(h::add))
                .retrieve()
                .bodyToMono(responseType);
    }

    /**
     * 获取 Mono 对象用于 POST 请求
     *
     * @param webClient 用于本次处理的 {@code webClient} 数据
     * @param apiRequestAnnotation 用于本次处理的 {@code apiRequestAnnotation} 数据
     * @param requestData 请求参数及可信连接覆盖信息
     * @param params 用于本次处理的 {@code params} 数据
     * @param responseType 响应反序列化类型
     * @param path 用于本次处理的 {@code path} 数据
     * @param <RES> 响应数据类型
     * @return 满足本方法约束的 {@code Mono<RES>} 结果
     */
    private <RES extends IAPIResponseData> Mono<RES> _getMonoForPost(
            WebClient webClient, APIRequestData apiRequestAnnotation,
            IAPIRequestData requestData, Map<String, Object> params,
            Class<RES> responseType, String path) {
        boolean isJson = apiRequestAnnotation.isJson();
        if(isJson) {
            return webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> requestData.headers().forEach(h::add))
                    .bodyValue(requestData)
                    .retrieve()
                    .bodyToMono(responseType);
        }
        return webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(h -> requestData.headers().forEach(h::add))
                .body(BodyInserters.fromFormData(_toMultiValueMap(params)))
                .retrieve()
                .bodyToMono(responseType);
    }

    /**
     * 从 IAPIRequestData 对象中提取参数
     *
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
     *
     * @param builder 用于本次处理的 {@code builder} 数据
     * @param path 用于本次处理的 {@code path} 数据
     * @param params 用于本次处理的 {@code params} 数据
     * @return 满足本方法约束的 {@code URI} 结果
     */
    private static URI _buildUri(UriBuilder builder, String path, Map<String, Object> params) {
        var b = builder.path(path);
        params.forEach(b::queryParam);
        return b.build();
    }

    /**
     * 构造 MultiValueMap 用于 POST 请求
     *
     * @param map 参数映射
     * @return 由原查询或表单参数转换得到的多值映射
     */
    private static MultiValueMap<String, String> _toMultiValueMap(Map<String, Object> map) {
        MultiValueMap<String, String> m = new LinkedMultiValueMap<>();
        map.forEach((k, v) -> m.add(k, v.toString()));
        return m;
    }

    /**
     * 返回第一个非空白字符串。
     *
     * @param primary 优先值
     * @param fallback 回退值
     * @return 第一个非空白字符串；如果都为空则返回 {@code null}
     */
    private static String _firstNotBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    /**
     * 响应缓存类，用于存储 API 响应，防止重复请求
     *
     * @param responseType 缓存的响应类型
     * @param responseData 缓存的响应数据
     * @param <RES> 本类型约定的泛型数据类型
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record ResponseCache<RES extends IAPIResponseData>(Class<RES> responseType, RES responseData) {}
}
