# 一. Config 模块结构介绍

`config` 模块把 `teanekocore.database.configdata` 的配置能力包装成聊天命令，并用客户端命名空间与消息作用域限制配置的可见范围和可注册范围。

| 类或注解 | 作用 |
|:---:|---|
| `@ConfigNamespace` | 标注在 `ITeaNekoClient` 实现类上，为该客户端声明 general/private/group 可用配置命名空间。 |
| `TeaNekoConfigNamespaces` | 提供内置命名空间常量：`teaneko-general`、`teaneko-private`、`teaneko-group`。 |
| `TeaNekoConfigKey` | `IConfigKey` 实现，根据 scope ID、用户 UUID 或客户端 + 群 ID 生成配置 key。 |
| `TeaNekoConfigCommand` | `/config`、`/cfg`、`/配置` 命令，提供配置查询、注册、注销和字段修改。 |

# 二. 命名空间规则

`TeaNekoConfigCommand` 会根据当前命令上下文构造可访问命名空间：

| 场景 | 加入的命名空间 |
|---|---|
| 任意场景 | `TeaNekoConfigNamespaces.GENERAL` 和客户端注解中的 `generalNamespace()`。 |
| 私聊 | `TeaNekoConfigNamespaces.PRIVATE` 和客户端注解中的 `privateNamespace()`。 |
| 群聊 | `TeaNekoConfigNamespaces.GROUP` 和客户端注解中的 `groupNamespace()`。 |

注册配置时，命令会检查目标配置的 namespaces 是否与当前可访问 namespaces 有交集。没有交集时会拒绝注册。

# 三. 配置命令

| 命令 | 作用 |
|---|---|
| `/cfg [配置名称]` | 查询当前 scope 已注册配置；不传名称时列出全部。 |
| `/cfg all [配置名称]` | 查询当前客户端和 scope 可开启的配置；不传名称时列出全部可用配置。 |
| `/cfg reg <配置名称>` 或 `/cfg register <配置名称>` | 在当前 scope 注册配置；重复注册会按底层服务规则重置/覆盖。 |
| `/cfg unreg <配置名称>` | 注销当前 scope 的配置，并删除该配置数据。 |
| `/cfg set <配置名称> <字段> <值...>` | 修改配置字段。 |
| `/cfg add <配置名称> <字段> <值...>` | 向 list 字段追加值。 |
| `/cfg remove <配置名称> <字段> <index>` | 从 list 字段按索引删除值。 |
| `/cfg clear <配置名称> <字段>` | 清空 list 字段。 |

# 四. 核心 API

| API | 说明 |
|---|---|
| `@ConfigNamespace.generalNamespace()` | 声明客户端可访问的通用配置命名空间。 |
| `@ConfigNamespace.privateNamespace()` | 声明客户端私聊可访问配置命名空间。 |
| `@ConfigNamespace.groupNamespace()` | 声明客户端群聊可访问配置命名空间。 |
| `new TeaNekoConfigKey(String scopeId)` | 直接使用 scope ID 构造配置 key。 |
| `new TeaNekoConfigKey(UUID userId)` | 使用私聊用户 UUID 构造 `private@<uuid>` key。 |
| `new TeaNekoConfigKey(ITeaNekoClient, String groupId)` | 使用客户端和群 ID 构造 `<clientId>-group@<groupId>` key。 |
| `TeaNekoConfigKey.getKey()` | 返回最终配置 key。 |

# 五. 依赖的 core 服务

| 服务 | 用途 |
|---|---|
| `ConfigManagerScanner` | 根据配置名称找到配置管理器定义。 |
| `IConfigDataService` | 注册、注销、设置字段、修改 list 字段。 |
| `IConfigDataQueryService` | 查询配置定义和当前 scope 的已注册配置。 |
| `ObjectFieldUtil` | 判断并操作 list 类型字段。 |

# 六. 注意事项

| 项 | 说明 |
|---|---|
| 权限 | `/config` 命令要求 `CommandPermission.OWNER`，并声明 `teaNeko-config` 权限包。 |
| group key | 群配置 key 依赖 `clientId`，修改客户端 ID 会影响历史群配置读取。 |
| 私聊 key | 私聊配置 key 依赖 TeaNeko 用户 UUID，而不是平台用户 ID。 |
| 命名空间边界 | 当前命令层主要在注册和查询时按命名空间过滤；底层配置服务仍应自行保证数据一致性。 |
