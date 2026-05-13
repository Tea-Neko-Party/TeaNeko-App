# 一. Database 结构介绍

`database` 是 TeaNeko Core 的持久化辅助模块，建立在 Spring Data JPA、`actuator` 任务系统和 `cache` 模块之上。它把数据库写入、缓存更新、并发重试和配置数据查询封装为统一 API。

| 子模块 | 作用 |
|:---:|---|
| `base` | 数据库任务基础设施，负责事务任务、缓存任务、重试阶段和统一提交。 |
| `easydata` | 通用 key-value 数据表模型，支持按表、namespace、target 获取 DTO。 |
| `configdata` | 基于 EasyData 的配置管理，支持注册、查询、字段级修改和字段校验器。 |
| `itemdata` | 面向物品或库存类数据的持久化模型，支持 owner、namespace、type、count 和 metadata。 |

# 二. Base 数据库任务

`base` 的目标是把一次数据库修改拆成两段：

```markdown
1. transaction task: 在事务内执行，失败则回滚。
2. cache task: 事务成功后执行，用于更新内存缓存或触发轻量后置逻辑。
3. future chain: 数据库任务完成后由调用方处理结果或异常。
```

| API | 说明 |
|---|---|
| `IDatabaseService.pushQuickTask(taskName, transactionCallback, cacheCallback)` | 快速提交一个事务任务和可选缓存任务。 |
| `DatabaseTaskConfig.addTransactionTask(VoidCallable)` | 追加事务内任务。 |
| `DatabaseTaskConfig.addCacheTask(VoidCallable)` | 追加事务成功后的缓存任务。 |
| `DatabaseTaskConfig.merge(IDatabaseTaskConfig)` | 合并两个尚未提交的数据库任务；被合并任务会被标记为已提交。 |
| `DatabaseTaskConfig.push()` | 提交任务并自动 `finish()`。 |
| `DatabaseTaskConfig.pushWithFuture()` | 提交任务并返回 `TaskFuture<ITaskResult<Void>>`。 |
| `DatabaseRetryHandlingTaskStage` | 处理数据库并发和完整性异常，将可重试异常包装为 `TaskRetryRuntimeException`。 |

默认任务阶段 namespace 是：

```java
DatabaseService.TASK_STAGE_NAMESPACE // "general_database"
```

默认最大重试次数来自：

```properties
tea-neko.database.max-retry-count=8
```

# 三. EasyData

EasyData 是一套通用 key-value 数据持久化模型。数据维度是：

```markdown
table(entity class) + namespace + target + key -> value
```

| 类或接口 | 作用 |
|---|---|
| `BaseEasyDataObject` | JPA `@MappedSuperclass`，定义 `id`、`namespace`、`target`、`key`、`value`、`version`。 |
| `BaseEasyDataRepository<T>` | 通用 Repository，提供按 namespace/target/key 查询、删除和 upsert。 |
| `IEasyData` / `BaseEasyData` | 面向业务的 EasyData 入口，指定 entity class 和 namespace。 |
| `IEasyDataDto` / `EasyDataDto` | 某个 target 下的数据 DTO，懒加载数据库数据并提供 typed getter。 |
| `IEasyDataDtoTaskConfig` | 修改 EasyData 的数据库任务，支持 `set`、`setBoolean`、`remove`。 |
| `EasyDataRepositoryScanner` | 扫描 `BaseEasyDataRepository` 子类，并按实体类映射 Repository。 |
| `EasyDataService` | 根据 `IEasyData` 和 target 获取缓存的 `EasyDataDto`。 |
| `@EasyData` | 标注 EasyData 实现类的任务阶段 namespace；为空时使用数据库默认 namespace。 |

示例：

```java
IEasyDataDto dto = GeneralEasyData.of("plugin-a").get("target-id");
String value = dto.get("key");

dto.getTaskConfig("update config")
        .set("key", "new-value")
        .push();
```

内置表实现：

| 类 | 表 | 作用 |
|---|---|---|
| `GeneralEasyData` | `general_data` | 通用配置或业务 key-value。 |
| `CleanableEasyData` | `cleanable_data` | 可清理数据。 |
| `DebugEasyData` | `debug_data` | 调试数据。 |

# 四. ConfigData

`configdata` 在 EasyData 上提供配置管理器能力。一个 `@ConfigManager` 表示一组可注册、查询和修改的配置规则。

| API | 说明 |
|---|---|
| `@ConfigManager(value, description, namespaces, configType, fieldChecker, enabled)` | 标记配置管理器 Bean。`value` 是规则名，`namespaces` 是该规则可出现的域。 |
| `IConfigData` | 配置数据标记接口，要求能被 JSON 序列化和反序列化。 |
| `IConfigKey` | 配置 key 标记接口。 |
| `IConfigFieldChecker` | 字段写入前的校验器，返回 `null` 表示通过，返回字符串表示错误原因。 |
| `IConfigDataService.getConfigData(...)` | 获取指定 manager 和 key 的配置。 |
| `registerConfig(...)` / `unregisterConfig(...)` | 注册或注销配置。 |
| `setRuleConfigField(...)` | 通过字段名修改配置字段。 |
| `addToRuleConfigListFiled(...)` | 向配置中的 List 字段追加元素。 |
| `removeFromRuleConfigListFiled(...)` | 移除配置 List 字段的指定索引。 |
| `clearRuleConfigListFiled(...)` | 清空配置 List 字段。 |
| `IConfigDataQueryService` | 查询 manager 详情、对象配置详情和 namespace 下的规则详情。 |
| `ConfigManagerScanner` | 扫描启用的 `@ConfigManager`，建立 name 和 namespace 索引。 |

内置默认配置类型：

| 类 | 字段 |
|---|---|
| `BooleanDefaultConfigData` | `boolean value` |
| `LongDefaultConfigData` | `long value` |
| `StringDefaultConfigData` | `String value` |

# 五. ItemData

`itemdata` 用于管理某个 owner 在某个 namespace 下某类物品的数据，数据库唯一键是：

```markdown
owner_id + namespace + type
```

| 类或接口 | 作用 |
|---|---|
| `ItemDataObject` | JPA 实体，包含 `uuid`、`ownerId`、`namespace`、`type`、`metadataType`、`metadata`、`count`、`version`。 |
| `IItemMetadata` | metadata 标记接口。metadata 类需要支持 Jackson 序列化和反序列化。 |
| `@ItemMetadata("type")` | 注册 metadata 类型与 class 的映射。 |
| `EmptyItemMetadata` | 空 metadata 的默认实现，类型名为 `"null"`。 |
| `ItemMetadataScanner` | 扫描 `@ItemMetadata`，维护 type 与 metadata class 的双向映射。 |
| `IItemDataService.get(namespace, ownerId, type, clazz)` | 获取单个 DTO，不存在时返回 count 为 0 的 DTO。 |
| `IItemDataService.getByOwner(namespace, ownerId)` | 获取 owner 下全部 type 的 DTO。 |
| `IItemDataService.getByType(namespace, type)` | 获取某 type 下全部 owner 的 DTO。 |
| `IItemDataDTO` / `ItemDataDTO` | 物品数据 DTO，暴露 count、metadata 和数据库任务入口。 |
| `IItemDataDtoTaskConfig` | 支持 `addCount`、`reduceCount`、`setCount`、`setMetaData`。 |
| `IItemDataCacheService` | 缓存 DTO 和 owner 下 type 集合。 |

示例：

```java
var dto = itemDataService.get(
        "economy",
        ownerId,
        "coin",
        EmptyItemMetadata.class
);

dto.getDatabaseTaskConfig("add coin")
        .addCount(10)
        .push();
```

`reduceCount` 会在事务内再次校验数量，数量不足时抛出 `InsufficientItemCountException`，调用方可通过 `pushWithFuture()` 和 `TaskFuture.unwrapException(...)` 捕获。

# 六. 注意事项

| 场景 | 建议 |
|---|---|
| 数据库写入 | 不要直接改 DTO 内部缓存；通过 task config 提交数据库任务。 |
| 缓存一致性 | 事务任务成功后才执行缓存任务；事务失败时缓存不会更新。 |
| 并发冲突 | 数据完整性、乐观锁和约束冲突会被数据库 task stage 转换为可重试异常。 |
| EasyData value | 非字符串对象会用 JSON 序列化存储，读取失败会抛出 `JsonSerializationFailedException`。 |
| Config 字段修改 | 字段名通过反射匹配，List 字段操作会校验字段确实是 List。 |
