# 一. Response 模块结构介绍

`response` 模块处理外部平台对发送请求的响应。它通过 `echo` 找到发送时注册的 `TaskFuture`，把平台原始响应解析为调用方声明的响应类型，并完成对应任务。

| 类或接口 | 作用 |
|:---:|---|
| `ResponseData` | 客户端响应 DTO，包含 `success`、`echo` 和 `rawData`。 |
| `ResponseEvent` | 客户端收到响应后推入事件系统的事件。 |
| `IResponseService` / `ResponseService` | 管理 `echo -> (task key, sendData)` 注册表，并监听 `ResponseEvent` 完成 task。 |
| `ResponseEchoDuplicateException` | 重复注册 echo 时抛出。 |
| `IMessageSendResponseData` | 消息发送响应接口，至少提供 `getMessageId()`。 |
| `IPlatformUserResponseData` | 平台用户信息响应接口。 |
| `IGroupMemberResponseData` | 群成员信息响应接口。 |

# 二. 响应流程

```markdown
1. SenderService 发送前调用 ResponseService.register(echo, key, sendData)。
2. 外部平台返回响应，客户端适配器构造 ResponseData 并推送 ResponseEvent。
3. ResponseService.onResponseEvent(...) 读取 echo、success 和 rawData。
4. 如果 echo 未注册，记录错误日志并忽略该响应。
5. 如果 sendData.responseType 有效且 rawData 非空，使用 object mapper 转为 List<responseType>。
6. 调用 ITaskService.complete(key, new TaskResult<>(success, parsedData))。
7. 如果转换失败，调用 forceCompleteExceptionally(key, exception) 并记录报告日志。
8. SenderService 在 future 完成后 unregister echo。
```

# 三. 数据结构

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | `boolean` | 平台响应是否成功。常见平台可从 `status == "ok"` 转换而来。 |
| `echo` | `String` | 匹配发送请求的唯一标识。 |
| `rawData` | `List<Map<String, Object>>` | 平台响应原始数据，由 `ResponseService` 转换成调用方声明的响应类型列表。 |

# 四. 核心 API

| API | 说明 |
|---|---|
| `IResponseService.register(String, UUID, ISendData<?>)` | 注册一个待响应的发送请求。 |
| `IResponseService.unregister(String)` | 删除一个 echo 的注册信息，通常由发送 future 完成回调执行。 |
| `ResponseService.onResponseEvent(ResponseEvent)` | 事件监听入口，优先级为 `Integer.MIN_VALUE`。 |
| `IMessageSendResponseData.getMessageId()` | 获取发送成功后的平台消息 ID。 |
| `IPlatformUserResponseData.getPlatformUserId()` | 获取平台用户唯一 ID。 |
| `IPlatformUserResponseData.getLevel()` | 获取平台用户等级。 |
| `IPlatformUserResponseData.getNickname()` | 获取平台用户昵称。 |
| `IGroupMemberResponseData` getter | 获取群成员昵称、用户 ID、群名片、入群时间、最后发言时间、等级、头衔和角色。 |

# 五. 解析规则

| 条件 | 行为 |
|---|---|
| `responseType == null` | 完成空列表结果。 |
| `responseType == Void.class` | 完成空列表结果。 |
| `rawData == null` 或为空 | 完成空列表结果。 |
| `sendData.getObjectMapper() != null` | 优先使用发送数据自带 mapper。 |
| 未提供 mapper | 使用默认 `JsonMapper`，并启用 `ACCEPT_SINGLE_VALUE_AS_ARRAY`。 |
| 解析异常 | 强制异常完成 task，并写入 `ILogger#errorWithReport`。 |

# 六. 注意事项

| 项 | 说明 |
|---|---|
| 不建议外部直接调用 | 业务代码通常通过 `sender` 发送并等待 future，不直接操作 `ResponseService`。 |
| echo 必须可匹配 | 平台响应必须原样带回发送时的 echo，否则响应会被记录为未注册并忽略。 |
| rawData 形态 | 当前实现把 rawData 转为 `List<responseType>`，即使单对象响应也会按列表处理。 |
