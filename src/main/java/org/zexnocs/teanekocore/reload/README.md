# 一. Reload 结构介绍

`reload` 提供统一的热重载入口。所有需要扫描或重新加载运行期数据的模块都可以实现 `IReloadable`，由 `ReloadService` 在启动和手动 reload 时统一管理。

| 区域 | 作用 |
|:---:|---|
| `api` | `IReloadable` 和 `IScanner` 基础接口。 |
| `interfaces` | `IReloadService` 服务接口。 |
| `AbstractScanner` | 扫描器抽象基类，封装初始化、清理和重扫流程。 |
| `ReloadService` | 扫描所有 `IReloadable` Bean，并按优先级初始化和重载。 |

# 二. 核心流程

```markdown
1. Spring 创建 ReloadService。
2. ReloadService 在 @PostConstruct 中扫描所有 IReloadable Bean。
3. 按 getPriority() 从大到小排序。
4. 对每个 reloadable 调用 init()，然后加入管理列表。
5. 调用 reloadAll() 时，按管理列表顺序调用 reload()。
6. 单个 reloadable 失败会被 ILogger#errorWithReport 记录，不会阻断后续对象 reload。
```

# 三. 主要 API

| API | 说明 |
|---|---|
| `IReloadable.reload()` | 热重载逻辑。 |
| `IReloadable.init()` | 初始化逻辑。通常用于防止启动阶段重复加载。 |
| `IReloadable.getPriority()` | 重载优先级，数值越大越先 init。默认 `0`。 |
| `IScanner` | 扫描器标记接口，继承 `IReloadable`。 |
| `IReloadService.addReloadable(IReloadable)` | 手动加入可重载对象，并立即调用一次 `init()`。 |
| `IReloadService.reloadAll()` | 同步调用所有已管理对象的 `reload()`。 |
| `AbstractScanner._scan()` | 子类实现的扫描逻辑。 |
| `AbstractScanner._clear()` | 子类实现的清理逻辑。 |

# 四. AbstractScanner 行为

| 方法 | 行为 |
|---|---|
| `init()` | 只在第一次调用时执行 `_scan()`。 |
| `reload()` | 每次都先 `_clear()`，再 `_scan()`。 |

示例：

```java
@Service
public class MyScanner extends AbstractScanner {
    @Override
    protected void _scan() {
        // build cache
    }

    @Override
    protected void _clear() {
        // clear cache
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
```

# 五. 注意事项

| 场景 | 说明 |
|---|---|
| 初始化顺序 | 依赖其它扫描器结果的模块应设置更低优先级，让依赖先加载。 |
| 同步执行 | `reloadAll()` 是同步调用，不会自动并行。 |
| 扫描缓存 | 扫描器应在 `_clear()` 中清理全部旧数据，避免 reload 后残留过期映射。 |
