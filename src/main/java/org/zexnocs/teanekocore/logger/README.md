# 一. Logger 结构介绍

`logger` 提供 TeaNeko Core 内部统一日志接口。其它模块依赖 `ILogger`，从而避免直接绑定具体日志实现。

| 类或接口 | 作用 |
|:---:|---|
| `ILogger` | 日志接口，定义 `info`、`error`、`warn`、`debug` 和 `errorWithReport`。 |
| `DefaultLogger` | 基于 SLF4J 的默认实现。 |
| `LoggerReportData` | 错误报告数据结构，包含 namespace、message、throwable、reportRecipients 和 enableReport。 |

# 二. 主要 API

| API | 说明 |
|---|---|
| `info(namespace, message)` | 记录普通信息。 |
| `error(namespace, message)` | 记录错误信息。 |
| `error(namespace, message, throwable)` | 记录错误和异常堆栈。 |
| `warn(namespace, message)` / `warn(namespace, message, throwable)` | 记录警告。 |
| `debug(namespace, message)` / `debug(namespace, message, throwable)` | 记录调试信息。 |
| `errorWithReport(namespace, message, throwable, recipients)` | 构造 `LoggerReportData` 并交给实现处理。 |
| `errorWithReport(LoggerReportData)` | 面向实现类的统一错误报告入口。 |

# 三. 默认实现

`DefaultLogger` 使用：

```java
LoggerFactory.getLogger(DefaultLogger.class)
```

异常日志会通过 `ExceptionUtils.instance.buildExceptionMessage(throwable)` 展开异常类型、消息、堆栈、suppressed 和 cause。

# 四. 使用示例

```java
logger.info("CommandScanner", "scan command finished");

logger.errorWithReport(
        "TaskService",
        "task failed",
        throwable
);
```

# 五. 注意事项

| 场景 | 说明 |
|---|---|
| report | 当前 `DefaultLogger#errorWithReport` 只写日志，`LoggerReportData` 中的接收人字段为后续通知实现预留。 |
| namespace | 建议使用模块名或类名，方便定位来源。 |
| 异常为空 | `ExceptionUtils` 接收 `null` 时会输出空字符串结果，调用方可按需要避免传空异常。 |
