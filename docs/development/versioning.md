# 版本与 Git 更新入口

版本唯一来源是根目录 version.properties。三段数字继续使用原有主/次/修订规则，Agent 发布额外添加 `-l`；不建立第二套版本文件或独立计数。修改这些任务不会立即更改当前版本、提交、标签或远程仓库。

发布内容以 [版本日志](../changelog/README.md) 为准。4.5.0-l、4.6.0-l 与 4.6.2-l 已按功能、兼容及验收边界整理；generateGitChangelog 仍会写入同名文件，重新执行前须备份并审阅，避免用自动提交列表覆盖已整理内容。该任务只统计 HEAD 中已有提交，未提交的功能不会自动进入日志。

## 入口与隔离

| 任务 | 行为 |
| --- | --- |
| bumpMajor / bumpMinor / bumpPatch | 保留原入口，生成普通 X.Y.Z；从带后缀版本升级时去除后缀。 |
| bumpAgentMajor / bumpAgentMinor / bumpAgentPatch | 升级相同三段数字并使用 X.Y.Z-l，不重复追加后缀。 |
| generateGitChangelog | 读取本次执行后的版本，写入 docs/changelog/完整版本.md；排除当前标签，从上一可达标签开始。 |
| createGitTag | 为当前已提交的完整版本创建 v版本 注解标签。 |
| createAgentGitTag | 同上，但要求当前版本后缀为 -l。 |
| agentGitPreview | 只读展示目标版本、标签和当前已暂存文件。 |
| agentGitUpdate | 校验、保存 Agent 版本、仅自动暂存 version.properties、提交现有暂存区、创建精确标签；不 push。 |

例如当前 4.4.1：普通 bumpMinor 得到 4.5.0，bumpAgentMinor 得到 4.5.0-l；从 4.5.0-l 执行普通 bumpPatch 得到 4.5.1，Agent bumpPatch 得到 4.5.1-l。主版本升级清零后两段，次版本升级清零修订号。

一次构建只允许一个升级入口和一个标签入口；普通/Agent bump 混用、多个 bump、agentGitUpdate 与 bump/独立标签混用均在任务写入前拒绝。agentGitUpdate 也不与 generateGitChangelog 混用，避免提交后才生成日志；应按下方两步操作。不同调用仍应串行执行，不能在同一工作树并发运行发布任务。构建产物名称和 BootJar 清单使用本次执行后的版本。

## 提交选定内容并打标签

先审阅并暂存要提交的文件，再预览和执行。下例从 4.4.1 升级到 4.5.0-l；路径仅为示例，应替换为本次需要提交的内容。

```powershell
git diff
git add -- src/main/java/org/zexnocs/teanekoagent/adapter/command/README.md
git diff --cached
.\gradlew.bat agentGitPreview -PagentBump=minor
.\gradlew.bat agentGitUpdate -PagentBump=minor "-PagentCommitMessage=feat: 更新 Agent 功能"
```

agentBump 接受 none、major、minor、patch，默认 none：只把当前三段数字标记为 -l，不自动加一。提交说明 agentCommitMessage 必须显式填写，作为单个进程参数传递，支持空格与多行，不按 shell 命令执行。

任务不会自动 git add 整个项目；原来已暂存的全部内容都会进入提交，未暂存的其他文件保留。版本文件会整体暂存，因此执行前也应审阅其中的其他改动。任务不代替编译、测试或发布前检查。

## 同时准备更新日志

先升级并生成日志，审阅后再暂存并提交；第二步使用默认 none，不重复升级。

```powershell
.\gradlew.bat bumpAgentMinor generateGitChangelog
git diff -- version.properties docs/changelog/4.5.0-l.md
git add -- version.properties docs/changelog/4.5.0-l.md
# 再暂存本次已经审阅的代码文件。
.\gradlew.bat agentGitPreview
.\gradlew.bat agentGitUpdate "-PagentCommitMessage=feat: 发布 4.5.0-l"
```

普通入口继续使用 bumpMinor 等任务、人工提交和 createGitTag。所有标签任务都要求 version.properties 已进入 HEAD，避免给尚未提交的版本命名。其他未提交文件不被标签快照包含。

## 失败与恢复

- 非仓库根目录、没有 HEAD、分离 HEAD、未解决冲突、提交身份缺失、目标标签已存在或无可提交内容时拒绝更新；可提前完成的检查均在保存版本前执行。
- 标签不强制覆盖；独立标签任务仅在同名标签已经指向当前 HEAD 时幂等成功，指向其他提交时失败。
- Git 失败使 Gradle 任务失败，不吞异常。版本写入、提交、标签不是原子事务：钩子失败可能留下版本及暂存改动，打标签失败可能已有提交。先检查 git status、git log -1 和目标标签，不盲目重跑升级入口。
- 不自动 reset、清理工作区、回退提交、删除标签或推送。远程发布需另行审阅并显式执行 push。
- 发布任务使用当前项目及任务图，暂不支持 Gradle configuration cache。

## 验证记录

2026-11-01：4 项 Kotlin/Git 关键测试全部通过，覆盖版本通道、真实提交范围/注解标签、只读预览/冲突前置拒绝和提交失败保留。强制 Kotlin 编译及当前项目编译/JAR 验证通过，最终运行没有弃用警告。

隔离副本实际执行 Gradle：Agent 升级得到 4.5.0-l，日志文件、普通 JAR 和 BootJar 清单一致；提交只包含已选文件、版本及已暂存日志；混合升级及提交/日志混用均在写入前失败；原 bumpPatch 继续得到普通 4.5.1 及对应 JAR。当前项目 51 个受影响生产/内部类的实际 JAR 字节与编译结果一致。

所有测试提交和标签仅发生在隔离仓库，当前项目的 version.properties、索引、HEAD 和全部标签与执行前完全一致，没有真实发布或推送。本次没有修改 Java 业务行为，未重复执行既有业务回归。证据位于 build/tmp/versioning：buildsrc-delivery.log、project-delivery.log、cli-agent-prepare.log、cli-agent-commit.log、cli-mixed-conflict.log、cli-log-conflict.log、cli-normal-version.log 和 verification.json。
