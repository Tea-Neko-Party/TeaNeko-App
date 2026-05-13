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
| `IAPIRequestData.headers()` | 返回请求头，默认空 Map。 |
| `IAPIResponseData` | 响应 DTO 标记接口。 |
| `IAPIResponseService.addTask(requestData, responseType)` | 使用缓存策略发起请求。 |
| `IAPIResponseService.addTask(requestData, responseType, disableCache, skipCache)` | 细粒度控制缓存。`disableCache=true` 表示不写缓存；`skipCache=true` 表示不读缓存。 |

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

# 五. 注意事项

| 项 | 说明 |
|---|---|
| 缓存时间 | `@APIRequestData.cacheDurationInMillis` 当前是请求描述的一部分；实际缓存容器的默认过期策略由 `cache` 模块管理。 |
| POST JSON | `isJson=true` 时直接把整个 requestData 作为 JSON body。 |
| POST 表单 | `isJson=false` 时使用提取出的字段参数构造 form body。 |
| 参数默认值 | `@APIRequestParam.defaultValue` 只对 `null` 字段生效，并只显式处理 `int/Integer`、`boolean/Boolean` 和字符串可 cast 类型。 |
| 异常处理 | 返回的是 `TaskFuture<RES>`，链尾仍应调用 `finish()` 或自定义异常处理。 |
