# 一. Cache 结构介绍

`cache` 提供轻量级缓存容器和统一清理服务。它不限定缓存值类型，主要用于模块内部的扫描结果、DTO、API 响应和任务缓存。

| 类或接口 | 作用 |
|:---:|---|
| `ICacheService` / `CacheService` | 管理所有缓存容器，并周期性调用自动清理。 |
| `ICacheContainer` | 缓存容器接口，提供 `manualClean()` 和 `autoClean(long)`。 |
| `ConcurrentMapCacheContainer<K, V>` | 基于 `ConcurrentHashMap` 的通用缓存容器。 |
| `ICacheData<V>` / `CacheData<V>` | 包装缓存值、访问时间、过期判定和过期回调。 |
| `ICacheDataFactory<V>` / `CacheDataFactory<V>` | 为缓存容器创建 `ICacheData`。 |

# 二. 工作方式

`CacheService` 启动后会通过 `TimerService.registerBySmartRate` 注册清理任务，默认清理周期来自：

```properties
tea-neko.cache.general-clean-rate-ms=1000
```

清理流程：

```markdown
1. 各模块通过 ConcurrentMapCacheContainer.of(cacheService, ...) 创建缓存。
2. 容器创建后自动注册到 CacheService。
3. CacheService 周期性调用每个容器的 autoClean(currentTimeMs)。
4. 容器按自身 cleanIntervalMs 判断是否需要扫描。
5. 数据过期时调用 ICacheData.onExpire(...)。
6. onExpire 返回 true 时删除缓存；返回 false 时保留，等待下次清理。
```

# 三. 主要 API

| API | 说明 |
|---|---|
| `ConcurrentMapCacheContainer.of(cacheService)` | 创建默认缓存：数据默认 1 小时过期，清理间隔 1 分钟，参与手动清理。 |
| `of(cacheService, expireTimeMs)` | 指定过期时间，清理间隔为过期时间的 `1/60`。 |
| `of(cacheService, expireTimeMs, cleanIntervalMs)` | 指定过期时间和清理间隔。 |
| `of(cacheService, expireTimeMs, participateInManualClean)` | 指定是否参与 `manualCleanAll()`。 |
| `of(cacheService, cleanIntervalMs, ICacheDataFactory, participateInManualClean)` | 使用自定义缓存数据工厂。 |
| `put(K, V)` / `put(K, ICacheData<V>)` | 写入缓存。 |
| `get(K)` | 读取缓存并刷新访问时间。 |
| `computeIfAbsent` / `computeIfPresent` / `compute` | 原子式计算并写回缓存。 |
| `remove(K)` / `containsKey(K)` | 删除和判断 key。 |
| `ICacheService.manualCleanAll()` | 调用所有缓存容器的手动清理。 |

# 四. 使用示例

```java
ConcurrentMapCacheContainer<String, User> cache =
        ConcurrentMapCacheContainer.of(cacheService, 10 * 60_000L);

User user = cache.computeIfAbsent("10001", id -> loadUser(id));
cache.put("10002", new User());
```

自定义过期回调：

```java
cache.put("resource", new CacheData<>(
        resource,
        60_000L,
        (now, value) -> {
            value.close();
            return true;
        }
));
```

# 五. 注意事项

| 场景 | 建议 |
|---|---|
| `onExpire` | 该方法在缓存清理线程中执行，应保持轻量，不要在其中反向访问或修改同一个缓存容器。 |
| 手动清理 | 只有创建容器时 `participateInManualClean=true` 才会响应 `manualCleanAll()`。 |
| 过期时间 | `CacheData` 的过期基于最后访问时间，`get()` 和写入会刷新访问时间。 |
| 并发 | 容器底层使用 `ConcurrentHashMap`，但过期清理会遍历 entry，过期回调要避免复杂并发副作用。 |
