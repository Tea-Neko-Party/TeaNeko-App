# 一. Event 结构介绍

`event` 是 TeaNeko Core 的事件分发模块。它使用 `actuator` 提交事件处理任务，使用扫描器发现事件类型和事件监听器，并支持取消、优先级、异步监听器和事件链。

| 区域 | 作用 |
|:---:|---|
| `interfaces` | 事件、事件服务和泛型类型服务接口。 |
| `core` | `@Event`、`@EventListener`、`@EventHandler` 和对应扫描器。 |
| `AbstractEvent` | 快速实现事件的抽象基类。 |
| `EventService` | 事件推送和监听器调度实现。 |
| `EventGeneralizedTypeService` | 解析并缓存 `IEvent<T>` 的泛型实际类型。 |

# 二. 事件模型

| 类或接口 | 作用 |
|---|---|
| `IEvent<T>` | 事件接口，提供 data、取消状态、通知前后钩子和下一事件。 |
| `AbstractEvent<T>` | 默认实现 data 和取消逻辑，提供 `_beforeNotify()`、`_afterNotify()`、`_getNextEvent()` 默认空实现。 |
| `@Event(value, namespace)` | 标记事件类。`value` 可被 `EventScanner` 注册为 key；`namespace` 用于事件处理任务阶段链。 |
| `@EventListener` | 标记监听器 Bean。 |
| `@EventHandler(async, priority, ignoreCancelled, taskChainNamespace)` | 标记监听方法。 |
| `EventScanner` | 扫描带 `@Event` 且实现 `IEvent` 的事件类。 |
| `EventHandlerScanner` | 扫描监听器方法，按事件类型和父类事件继承关系建立处理器列表。 |
| `IEventService` / `EventService` | 推送事件，返回或自动处理 `TaskFuture`。 |

# 三. 执行流程

```markdown
1. 调用 eventService.pushEventWithFuture(event) 或 pushEvent(event)。
2. EventService 根据 @Event.namespace 选择任务阶段 namespace；为空则使用 event-process-default。
3. 事件处理被提交到 TaskService。
4. 处理线程调用 event._beforeNotify()。
5. EventHandlerScanner 返回当前事件类的监听器列表，包括父类事件监听器。
6. 同步监听器按 priority 从大到小执行。
7. 异步监听器被提交为新的 Task；taskChainNamespace 为空时使用 event-listener-default。
8. 若事件被取消，默认跳过不允许 ignoreCancelled 的监听器，并停止后续事件链。
9. 调用 event._afterNotify()。
10. 如果 event._getNextEvent() 返回非 null，则在同一处理线程继续处理下一事件，最多 10 次。
```

# 四. 主要 API

| API | 说明 |
|---|---|
| `IEventService.pushEventWithFuture(IEvent<?>)` | 推送事件并返回 `TaskFuture<ITaskResult<Void>>`，调用方应在链尾 `finish()`。 |
| `IEventService.pushEvent(IEvent<?>)` | 推送事件并自动 `finish()`，适合不关心结果的场景。 |
| `IEvent.isCancelled()` / `setCancelled(boolean)` | 事件取消控制。 |
| `IEvent.getAndSetCancelled(boolean)` | 原子读写取消状态。 |
| `IEvent._beforeNotify()` | 通知监听器前的钩子。 |
| `IEvent._afterNotify()` | 通知监听器后的钩子。 |
| `IEvent._getNextEvent()` | 返回下一个事件，形成事件链。 |
| `EventHandlerScanner.getEventHandlerList(...)` | 获取指定事件类的监听器列表。 |
| `EventGeneralizedTypeService.getGeneralizedActualType(...)` | 获取 `IEvent<T>` 的 `T` 类型。 |

# 五. 使用示例

```java
@Event(value = "user-login", namespace = "audit")
public class UserLoginEvent extends AbstractEvent<User> {
    public UserLoginEvent(User data) {
        super(data);
    }
}

@EventListener
public class UserEventListener {
    @EventHandler(priority = 100)
    public void onLogin(UserLoginEvent event) {
        // handle
    }

    @EventHandler(async = true, taskChainNamespace = "async-audit")
    public void audit(UserLoginEvent event) {
        // async handle
    }
}
```

# 六. 注意事项

| 场景 | 说明 |
|---|---|
| 异步监听器取消 | 异步监听器启动后，后续取消不保证能影响已经提交的异步任务。 |
| 监听方法签名 | 必须是 `public void method(SomeEvent event)`。 |
| 优先级 | 同步监听器按 `priority` 降序执行；异步监听器虽然会先被提交，但并发顺序不保证。 |
| 父类事件 | 子类事件会继承父类事件的监听器。 |
| 异常 | 任一同步监听器异常会中断当前事件处理，并通过事件处理任务的 `TaskFuture` 暴露。 |
