# 一. Utils 模块结构介绍

`utils` 模块放置 TeaNeko App 层的通用工具，目前主要包括作用域 ID 工具和版本号读取工具。

| 类 | 作用 |
|:---:|---|
| `TeaNekoScopeService` | 生成和解析命令、配置、权限使用的 scope ID。 |
| `VersionUtil` | 获取当前应用版本号。 |

# 二. TeaNekoScopeService

| API | 说明 |
|---|---|
| `getPrivateScopeId(UUID uuid)` | 返回 `private@<uuid>`。 |
| `getGroupScopeId(ITeaNekoClient client, String groupId)` | 返回 `<clientId>-group@<groupId>`。 |
| `fromGroupScopeId(String groupScopeId)` | 解析 group scope，返回 `(ITeaNekoClient, groupId)`。 |

group scope 的格式由正则 `(.+)-group@(.+)` 解析。解析后会通过 `TeaNekoClientScanner.getPair(clientId)` 找回客户端实例。

# 三. VersionUtil

| 来源 | 行为 |
|---|---|
| Spring 配置 `app.version` | 如果存在且不是 `unknown`，直接返回该值。 |
| Jar manifest | 如果 `app.version` 为 `unknown`，尝试读取 `TeaNekoAppApplication.class.getPackage().getImplementationVersion()`。 |
| 开发环境 | 如果前两者都不可用，返回 `dev`。 |

# 四. 注意事项

| 项 | 说明 |
|---|---|
| scope 格式稳定性 | scope ID 会用于配置、命令作用域和权限数据，格式变更会影响历史数据。 |
| group scope 解析 | `fromGroupScopeId` 找不到 clientId 时会抛出 `IllegalArgumentException`。 |
| clientId 约束 | 如果 clientId 中包含 `-group@` 这类特殊片段，可能影响 scope 可读性，建议保持简短稳定。 |
