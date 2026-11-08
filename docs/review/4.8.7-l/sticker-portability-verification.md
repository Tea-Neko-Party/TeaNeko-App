# v4.8.7-l 图库与目录增量验证

日期：2026/11/07。五项追加需求已完成。此记录补充[审查修复批次](verification.md)，不把前一批全量回归、MySQL 验收或产物哈希作为本批结果。

| 范围 | 实际结果 |
| --- | --- |
| Java | 54 项定向回归，0 失败、0 错误、0 跳过；包含 10 项新增迁移与概率用例 |
| 描述迁移 | 旧活动目录与独立删除行迁出正文，保留引用及删除决定；描述缺失冷启动明确失败，不擦除数据库引用 |
| 复制导入 | 原图及旁车复制到不同 catalog-id，直接可用且不调用模型；已有停用/删除不被覆盖，超出有效容量保持停用，坏文件不阻塞其他文件 |
| 事务 | 修改保存失败保留旧描述和旧旁车，未提交的不可变文件不被导入；成功编辑切换新引用 |
| 概率 | 动态曲线与静态边界、两种模式参数校验、生产 YAML 绑定；零概率跳过不占模型额度，同消息随后显式收藏仍执行，已有缓存不抽样 |
| 原流程 | 素材下载、采集、管理、发送租约、图库输入预算、临时存储及文件配置回归通过 |
| Chrome | 5 组检查通过、无页面异常；专用/通用筛选及清除、真实客户端 ID、参数分行、主题色按钮与参数弹窗；工具/指令页在 320/390px 无横向溢出，已查看截图 |
| 构建 | 前端类型检查及生产构建、Java 编译、18 文件 JavaDoc、增量代码规范及 git diff --check 通过 |
| 产物 | TeaNeko-App-4.8.7-l.jar 包含本地描述/导入/抽样新类、概率模板与更新后的工具/指令页面 |

初次构建 JAR SHA-256：`7B4DC7C4B0C7BFC2FCE79A2DBD0D5B72B5FFA2377B616FF84471C800DFB8F1B9`；维护入口修正后的产物见下方补记。

证据位于 `temp/verification/4.8.7-l/sticker-tests.log`、`sticker-final-build.log`、`sticker-portability/`，JavaDoc 位于 `temp/javadoc/4.8.7-l/sticker-portability/`。测试使用原隔离事务夹具、真实临时文件与本地模型/API 替身，没有修改现有业务库或请求外部模型。

现有图库的实际迁移在运行新版后发生。部署前备份完整数据库与素材目录；旧程序不识别新描述引用，回退必须恢复升级前的配套备份。迁移、默认概率及复制方法以[图库说明](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/README.md)为准。

## 定时维护预算修正

2026/11/07 用户实机报告 `LLMCallLimits.<init>` 每分钟报错。根因为维护入口使用 `new LLMCallLimits(0, 0, 0)`，违反模型交互至少一次请求的构造约束，便携导入尚未执行即失败。初次复制导入测试直接调用 `catalog.maintain()`，漏过了 `StickerCollectionService.tick()` 的预算创建。

维护现使用显式零模型、零工具及零 token 的 `ResourceBudget`，保留期限、取消和实时资格复核；模型交互的正数约束不变。检查其他生产调用点，未发现另一处同类零次构造误用。

| 验证 | 实际结果 |
| --- | --- |
| 先复现后修复 | 新增从 `tick()` 导入真实复制素材的回归；旧代码无法恢复条目，修复后恢复完整描述、可用状态且模型调用为零 |
| 定向回归 | 55 项通过，0 失败、0 错误、0 跳过，包含 11 项迁移与概率用例 |
| 万条迁移 | 首轮及独立复查均超过原 10 秒迁移等待；仅将批量真实文件迁移等待改为 30 秒、该方法总期限改为 60 秒，后续查询保持 10/5 秒。12,000 条记录的完整性、分页和容量断言通过，该用例总耗时 15.075 秒，不作为性能基准 |
| 构建 | Java 编译、3 文件 JavaDoc、`checkJavaChanges` 及 `bootJar` 通过；本次纯后台修复未重做浏览器验收 |
| 产物 | 启动目录与 JAR 中的 `StickerCollectionService.class` 摘要一致；用户的 `gradle.properties` 未修改 |

修正后 JAR SHA-256：`546EB40086062E12B34D067D3E827CE1FC6567187F508582058260CEAAE1DDBA`。

复现与回归证据在 `temp/verification/4.8.7-l/maintenance-budget-before.log`、`maintenance-budget-before.xml`、`maintenance-budget-fix.log`、`maintenance-budget-deletion-check.log`、`maintenance-budget-final.log`；JavaDoc 在 `temp/javadoc/4.8.7-l/maintenance-budget/`。本次仍使用隔离事务夹具与临时素材，未连接真实模型或业务数据库。
