# MySQL 安装、升级与恢复

v4.8.7-l 起，`prod` 使用 `ddl-auto=validate`。启动只检查表结构，建表和索引变更在停机部署时显式执行。`dev` 可继续使用独立数据库的自动建表。

## 安装与升级

先停止旧实例并备份。使用 JDK 25、MySQL Connector/J，以及与目标版本配套的 [迁移器](../../scripts/database/SchemaMigration.java)和 [SQL 目录](../../scripts/database/mysql)。Connector/J 已包含在发布 JAR 的 `BOOT-INF/lib/` 中；迁移器及 SQL 位于 `BOOT-INF/classes/schema/`，仅部署 JAR 时可用 `jar xf` 提取这三个部分。

| 步骤 | 操作 |
| --- | --- |
| 建库 | 使用迁移账号创建专用库，字符集为 `utf8mb4`；迁移器不会自行选择或创建数据库 |
| 设置连接 | 在当前进程环境中设置 `TEANEKO_MIGRATION_URL`、`TEANEKO_MIGRATION_USER`、`TEANEKO_MIGRATION_PASSWORD`。URL 必须包含目标库名，密码不要写进命令或提交到仓库 |
| 应用 | `java -cp <Connector/J的jar路径> scripts/database/SchemaMigration.java apply` |
| 复核 | `java -cp <Connector/J的jar路径> scripts/database/SchemaMigration.java verify` |
| 启动 | 切换为运行账号，执行 `./gradlew bootRun` 或启动配套 JAR；确认结构校验与应用启动成功 |

提取发布 JAR 后，源码路径改为 `BOOT-INF/classes/schema/SchemaMigration.java`，并在命令末尾指定 SQL 目录 `BOOT-INF/classes/schema/mysql`。以上路径含空格时加引号。

| 脚本 | 职责 |
| --- | --- |
| `V001__baseline.sql` | 创建六张业务表；已有表不覆盖数据、不重置自增 ID |
| `V002__maintenance_indexes.sql` | 补齐 Agent 近期查询、Agent 维护和临时表维护索引 |
| `verify.sql` | 核对 InnoDB、字段类型/长度/空值、三元唯一键及维护索引的列顺序 |

迁移器使用数据库互斥锁，成功脚本的 SHA-256 写入 `teaneko_schema_history`。重复运行会检查校验值；已执行脚本被修改、存在未知版本或结构不匹配时拒绝继续。历史迁移不应原地修改，后续变化添加新编号。语句以独立一行 `-- @statement` 分隔，不依赖客户端的 `DELIMITER`。

MySQL DDL 会隐式提交，整批脚本不承诺事务回滚。失败时保留日志，核对已执行语句后重跑幂等脚本；无法继续时恢复完整备份。不要删除迁移历史来跳过校验。

## 账号权限

运行账号只需目标库的 `SELECT、INSERT、UPDATE、DELETE`，不需要 `CREATE、ALTER、DROP`。迁移账号另外需要建表和建索引权限，备份/恢复账号按所用工具单独配置。不要给运行账号授予其他数据库权限。

例如数据库管理员可执行 `GRANT SELECT, INSERT, UPDATE, DELETE ON teaneko.* TO 'teaneko_app'@'实际来源主机';`。账号创建和密码交由部署环境管理；Docker 场景的连接来源不一定是 `localhost`。

## 备份与回退

1. 停止应用，保存实际 JAR、`version.properties`、`config/`、素材目录及其身份文件。记录应用版本与迁移历史。
2. 使用 `mysqldump --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF` 导出整个目标库；连接凭据使用工具的登录配置，不直接放入参数。备份包含业务表及迁移历史表。
3. 先恢复到一个空的独立数据库，核对所有表、行数、正文和索引，执行配套版本的 `verify`，再启动配套 JAR 验证。
4. 回退须一起恢复数据库、配置、素材与对应 JAR；仅换旧二进制不能撤回已经提交的数据或平台发送。

验收范围与实际结果见 [v4.8.7-l 验证记录](../review/4.8.7-l/verification.md)。独立测试库结果不能替代线上备份的恢复抽检。
