# 一. TeaUser 模块结构介绍

`teauser` 模块维护 TeaNeko 内部用户 UUID 与各平台用户 ID 之间的映射，并提供用户金币数据入口。它让跨平台业务可以使用稳定 UUID，而不是直接依赖某个平台的用户 ID。

| 类或接口 | 作用 |
|:---:|---|
| `ITeaUserService` / `TeaUserService` | 管理 `clientId + platformUserId -> UUID` 和反向查询。 |
| `ITeaUserCoinService` / `TeaUserCoinService` | 基于 item data 服务获取用户金币数据 DTO。 |

# 二. 用户映射

`TeaUserService` 使用 `GeneralEasyData` 存储平台用户到 TeaNeko UUID 的映射：

| 常量或结构 | 值 | 说明 |
|---|---|---|
| `TeaUserService.TO_TEA_USER_NAMESPACE` | `to_teaneko` | 用户映射所在 EasyData namespace。 |
| target | `client.getClientId()` | 每个客户端独立存储平台用户映射。 |
| key | `platformUserId` | 平台用户 ID。 |
| value | `UUID.toString()` | TeaNeko 内部用户 UUID。 |
| cache key | `(clientId, UUID)` | 反查平台用户 ID 的缓存 key。 |

# 三. 核心 API

| API | 说明 |
|---|---|
| `ITeaUserService.get(ITeaNekoClient, String userId)` | 根据客户端和平台用户 ID 查询 TeaNeko UUID，不存在返回 `null`。 |
| `ITeaUserService.getOrCreate(ITeaNekoClient, String userId)` | 查询或异步创建 TeaNeko UUID，返回 `TaskFuture<UUID>`。 |
| `ITeaUserService.getPlatformId(ITeaNekoClient, UUID)` | 根据客户端和 TeaNeko UUID 反查平台用户 ID。 |
| `ITeaUserService.getAll(ITeaNekoClient)` | 返回该客户端下所有已登记 TeaNeko UUID。 |
| `ITeaUserCoinService.getCoin(UUID)` | 直接根据 TeaNeko UUID 获取金币 item data DTO。 |
| `ITeaUserCoinService.getCoin(ITeaNekoClient, String)` | 先查询或创建 TeaNeko UUID，再获取金币 DTO。 |

# 四. 金币数据

| 常量 | 值 | 说明 |
|---|---|---|
| `TeaUserCoinService.DATABASE_NAMESPACE` | `tea-user` | item data namespace。 |
| `TeaUserCoinService.ITEM_TYPE` | `coin` | item type。 |

`getCoin(UUID)` 实际调用：

```java
iItemDataService.get("tea-user", userId, "coin");
```

# 五. 异常与修复行为

| 场景 | 行为 |
|---|---|
| 映射不存在 | `get(...)` 返回 `null`。 |
| 映射值不是合法 UUID | `get(...)` 会异步删除非法数据并返回 `null`。 |
| `getOrCreate(...)` 发现合法 UUID | 返回已经完成的 `TaskFuture<UUID>`，并写入反查缓存。 |
| `getOrCreate(...)` 无映射或 UUID 非法 | 写入新的随机 UUID，再返回当前存储值。 |

# 六. 注意事项

| 项 | 说明 |
|---|---|
| UUID 是内部身份 | 平台用户 ID 只用于映射；业务层应优先使用 TeaNeko UUID。 |
| clientId 影响隔离 | 不同客户端下相同平台用户 ID 会得到不同映射。 |
| 反查依赖缓存和扫描 | `getPlatformId(...)` 先查缓存，再按 value 从 EasyData 中反查 key。 |
| async 写入 | 新建 UUID 和清理非法 UUID 都通过 EasyData task 配置推送，调用方应处理 future。 |
