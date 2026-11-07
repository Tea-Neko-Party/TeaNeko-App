# 代码格式与增量检查

Java 使用项目的 [EditorConfig](../../.editorconfig) 和 IntelliJ `Project` 代码样式：四空格、一行一语句，展开控制分支和复杂异步块。IDE 导入项目后启用 EditorConfig；只格式化本次修改的方法，避免无关的全仓重排。

| 命令 | 检查范围 |
| --- | --- |
| `./gradlew checkJavaChanges` | 相对 `HEAD` 的工作区、暂存区及未跟踪 Java 文件 |
| `./gradlew checkJavaChanges -PqualityBase=<合并基线>` | CI 或审查时检查本分支相对基线的新增行 |
| `git diff --check` | 差异中的空白错误 |

`check` 已包含增量检查。检查器使用 JDK 语法树，区分字符串、文本块、注释和 `for` 头部，拒绝新增的密集语句、无大括号分支、真实替换字符和 Tab 缩进；历史未改动行不作为本次失败。它不替代类型检查、JavaDoc、IDE 检查或功能回归。

格式整理与行为变更分开核对。异步完成、锁、事务及资源关闭的位置必须逐项检查；仅有格式通过不能说明运行合同正确。
