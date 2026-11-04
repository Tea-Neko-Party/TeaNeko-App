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
| JAR 清单 | 优先读取当前应用产物的 `Implementation-Version`。 |
| 构建生成资源 | 普通类路径读取 `teaneko-version.properties`，由原版本管理器生成，源头仍是根目录 `version.properties`。 |
| 裸类路径 | 既无清单也无生成资源时返回 `dev`；已有资源损坏时明确失败。 |

4.7.1-l 起不再从 `app.version` 或 `-Dversion` 覆盖运行版本。Gradle `classes`、`bootRun` 和 JAR 构建都会生成资源；IDE 直接编译时先执行 `generateVersionInfo` 并同步 Gradle 资源目录。

# 四. 注意事项

| 项 | 说明 |
|---|---|
| scope 格式稳定性 | scope ID 会用于配置、命令作用域和权限数据，格式变更会影响历史数据。 |
| group scope 解析 | `fromGroupScopeId` 找不到 clientId 时会抛出 `IllegalArgumentException`。 |
| clientId 约束 | 如果 clientId 中包含 `-group@` 这类特殊片段，可能影响 scope 可读性，建议保持简短稳定。 |
