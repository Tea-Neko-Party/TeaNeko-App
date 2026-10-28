# 一. API Response 结构介绍

`api_response` 用于把 HTTP 请求描述为 Java 数据对象，并通过 `WebClient` 异步请求外部 API。返回值统一包装成 `TaskFuture<RES>`，因此可以和 `actuator` 的任务链一起使用。

| 区域 | 作用 |
|:---:|---|
| `api` | 请求、响应数据接口和请求注解。 |
| `interfaces` | 面向业务调用的服务接口。 |
| `exception` | 请求描述缺失、URL 错误等异常。 |
| `APIResponseService` | 请求执行、缓存、重试、超时和响应解析实现。 |

# 二. 核心功能

| 功能 | 说明 |
|---|---|
| 声明式请求 | 在请求类上使用 `@APIRequestData` 声明 baseUrl、path、HTTP method、超时和重试。 |
| 运行时覆盖 | 请求对象可重写 `getBaseUrlOverride()`、`getPathOverride()`、`getMethodOverride()`，用于从 file config 或数据库读取 API 地址。 |
| 参数提取 | 从请求对象字段中提取参数，可用 `@APIRequestParam` 指定参数名和默认值。 |
| GET/POST | GET 使用 query param；POST 支持 JSON body 和 `application/x-www-form-urlencoded`。 |
| Header 自定义 | 请求对象可重写 `IAPIRequestData.headers()`。 |
| 异步执行 | 使用 Reactor `Mono`、`Scheduler apiScheduler` 和 `TaskFuture` 返回结果。 |
| 响应缓存 | 默认缓存响应，缓存 key 由 baseUrl、参数 hash 和 response 类型组成。 |

# 三. 主要 API

| API | 说明 |
|---|---|
| `@APIRequestData(baseUrl, path, method, isJson, timeoutInMillis, retryCount, retryDelayInMillis, cacheDurationInMillis)` | 描述一个请求类。`baseUrl` 必填，`path` 建议以 `/` 开头且不要以 `/` 结尾。 |
| `@APIRequestParam(value, defaultValue)` | 标注字段对应的请求参数名。未标注时使用小写字段名。 |
| `IAPIRequestData.getBaseUrlOverride()` | 运行时覆盖 base URL；返回 `null` 时使用注解配置。 |
| `IAPIRequestData.getPathOverride()` | 运行时覆盖 path；返回 `null` 时使用注解配置。 |
| `IAPIRequestData.getMethodOverride()` | 运行时覆盖 HTTP method；返回 `null` 时使用注解配置。 |
| `IAPIRequestData.headers()` | 返回请求头，默认空 Map。 |
| `IAPIResponseData` | 响应 DTO 标记接口。 |
| `IAPIResponseService.addTask(requestData, responseType)` | 使用缓存策略发起请求。 |
| `IAPIResponseService.addTask(requestData, responseType, disableCache, skipCache)` | 细粒度控制缓存。`disableCache=true` 表示不写缓存；`skipCache=true` 表示不读缓存。 |
| `IAPIResponseService.addTask(requestData, responseType, disableCache, skipCache, execution)` | 显式 `ExecutionPolicy`，限制整个请求阶段并支持取消实际订阅。 |

# 四. 使用示例

```java
@APIRequestData(
        baseUrl = "https://api.example.com",
        path = "/v1/user",
        method = "GET"
)
public class UserRequest implements IAPIRequestData {
    @APIRequestParam("id")
    private final String userId;

    public UserRequest(String userId) {
        this.userId = userId;
    }
}

public class UserResponse implements IAPIResponseData {
    public String name;
}

apiResponseService.addTask(new UserRequest("10001"), UserResponse.class)
        .thenApply(user -> user.name)
        .finish();
```

运行时覆盖示例：

```java
@APIRequestData(baseUrl = "", path = "", method = "POST")
public class RuntimeUrlRequest implements IAPIRequestData {
    private final String baseUrl;
    private final String path;

    public RuntimeUrlRequest(String baseUrl, String path) {
        this.baseUrl = baseUrl;
        this.path = path;
    }

    @Override
    public String getBaseUrlOverride() {
        return baseUrl;
    }

    @Override
    public String getPathOverride() {
        return path;
    }
}
```

# 五. 注意事项

| 项 | 说明 |
|---|---|
| 缓存时间 | `@APIRequestData.cacheDurationInMillis` 当前是请求描述的一部分；实际缓存容器的默认过期策略由 `cache` 模块管理。 |
| POST JSON | `isJson=true` 时直接把整个 requestData 作为 JSON body。 |
| POST 表单 | `isJson=false` 时使用提取出的字段参数构造 form body。 |
| 参数默认值 | `@APIRequestParam.defaultValue` 只对 `null` 字段生效，并只显式处理 `int/Integer`、`boolean/Boolean` 和字符串可 cast 类型。 |
| 异常处理 | 返回的是 `TaskFuture<RES>`，链尾仍应调用 `finish()` 或自定义异常处理。 |


# 六. 运行时执行策略

执行策略独立于请求 DTO 和 headers，不参与 JSON 或参数提取：

```java
var policy = ExecutionPolicy.controlled(Duration.ofSeconds(30), control);
var response = apiResponseService.addTask(request, Response.class, true, true, policy);
response.asStage().whenComplete((value, failure) -> { /* 处理最终结果 */ });
// 需要停止所属执行时调用 control.cancel()。
```

`ExecutionPolicy.withTimeout(duration)` 仅限制本次阶段；`controlled(timeout, control)` 同时订阅已有执行的终止信号。timeout 为 null 时使用注解时限。显式 timeout 可以比注解更长或更短，有效时间再取阶段时限与 control.remainingTime 的较小值。control 由整轮拥有者在执行结束后 close；单个 HTTP 完成只解除自己的监听，不关闭共享 control 或调度器。

| 入口/结果 | 行为 |
| --- | --- |
| 旧两参/四参入口 | 保留注解的每次尝试超时和重试次数。 |
| 显式五参入口 | 阶段时限覆盖注解默认值，包含 apiScheduler 排队及重试等待；到期释放实际 WebClient 订阅。 |
| retryCount=0 | 不安装重试操作符，原始失败向上传播。 |
| 已取消/已过期 | Core 返回异常完成的 TaskFuture，不启动传输；运行期间终止也结束 Future。 |
| 阶段超时 | APIRequestTimeoutException；不会反向取消共享执行。 |
| 整轮截止时间/取消/关闭 | ExecutionStoppedException，保留 DEADLINE_EXCEEDED/CANCELLED/CLOSED 原因。 |
| 空响应正文 | 明确失败并完成 Future，不留下永久等待。 |
| 缓存命中/写入 | 命中同样检查执行状态；取消或到期后的迟到响应不写缓存。 |
| finish().cancel(...) | 取消根 Future 同时释放该 HTTP 订阅；asStage 仍是只读完成视图，取消它的副本不替代执行控制。 |

内部 APIRequestExecution 统一仲裁成功、失败、超时和取消；先登记订阅句柄再启动传输，避免同步完成或 onSubscribe 前取消漏掉资源。定时器、终止监听和订阅在终止后解除。响应已被接受后的取消不回滚已经完成的结果或副作用。

旧 IAPIResponseService 实现如果未适配五参方法，会明确抛出 UnsupportedOperationException，不能把执行策略静默忽略。Spring 的原有构造入口保持，测试使用内部装配入口注入手动时钟和 WebClient ExchangeFunction；不增加生产线程池或测试依赖。

默认离线验收包括真实 APIResponseService/WebClient 操作符链与受控 ExchangeFunction、手动调度器，不启动 Spring、数据库或外部 HTTP。订阅取消语义参考 [Reactor BaseSubscriber](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/BaseSubscriber.html)，实际依赖版本由现有 Gradle 构建决定。
