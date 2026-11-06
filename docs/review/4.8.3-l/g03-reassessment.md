# G03 修正独立复评

日期：2026/11/06。审查对象为 `4.8.3-l` 的继承工具代理修正。开始复评时 Git 基线为 `964d205`，包含用户未提交补丁；收尾时用户已提交至 `d884b5e` 并开始 `4.8.4-l` 工作区开发，G03 相关实现与正式测试没有新增未提交差异。本文保留 G03 的独立验证范围，不修改业务代码或既有测试，也不替代 [4.8.4-l 整体架构审查](../4.8.4-l/README.md)。

## 结论

G03 原触发条件已经消除，可以关闭。本次没有在该修正范围发现新的待修业务缺陷；新增工具仍可继承公共方法，通过各自 JDK 接口代理执行，不要求重复覆写父类或修改中央分派。G02 的 Bean 身份隔离、动态条件和原异步完成合同保留。

## 源码与功能核对

| 核对项 | 实际实现与证据 | 判断 |
| --- | --- | --- |
| 目录归属 | [compile:264](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java#L264) 按真实提供者获取接口，将业务和条件候选编入各自 `MethodSpec`；原全局 `Method → 接口候选` 缓存已移除 | 不同子类共享父类 Method 不再共用接口目录 |
| 原子发布 | [prepare:88](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java#L88) 用 `computeIfAbsent` 完整编译后发布，方法候选及规格列表为不可变副本 | 不暴露半成品；Bean 身份仅在编译成功后绑定 |
| 真实代理调用 | [invoke:405](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java#L405) 从本规格候选选择可接收实际 Bean 的方法并调用，不解包目标 | 不绕过 Spring advice；不把真实未公开方法的代理误当有效 |
| 高频路径 | [select:186](../../../src/main/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolMethods.java#L186) 和调用闭包只读规格；接口发现仍只在初始化编译发生 | 未恢复每轮扫描或惰性补发现 |
| 动态资格及异步完成 | 选择时执行条件，调用前和返回阶段后仍经 `requireAllowed`；原 CompletionStage 展开、执行控制和参数绑定保留 | 没有以缓存资格或提前成功替代实际执行 |
| 正反初始化与两个入口 | [正式测试:203](../../../src/test/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolTest.java#L203) 对不同 JDK 接口共享继承条件进行选择及实际调用；反向顺序使用全新声明类型，覆盖 Bean 入口和显式声明入口 | 排除旧静态缓存掩盖顺序问题，且观察条件/业务的实际 advice |
| 继承业务方法 | [正式测试:218](../../../src/test/java/org/zexnocs/teanekoagent/component/tool/TeaNekoAgentToolTest.java#L218) 验证两个独立宿主复用继承业务方法，输出仍来自各自实例 | 不通过覆盖父方法回避缺陷；同一注册目录的重名拒绝未放宽 |
| 旧问题与撤权 | 同接口多代理的 schema/client/requires、实际调用、调用前撤权及异步等待中撤权/取消仍在工具测试内 | 既有 G02 和执行边界回归通过 |

本轮检查的是上述修正及其调用链，不据此断言全部代理类型、全部 Agent 模块或新结构化输出已经无缺陷。

## 本轮实际验证

| 范围 | 结果 |
| --- | --- |
| 原 G03 隔离诊断 | 1 个测试类、1 项通过；未修改原诊断预期 |
| 相关正式回归 | 11 个测试类、65 项全部通过，0 失败、0 错误、0 跳过 |
| Gradle 最终运行 | `BUILD SUCCESSFUL`；编译任务为 up-to-date，两个测试任务实际重跑 |
| 外部与产物 | 未连接真实数据库、模型或平台；未重跑 bootJar、前端构建或 JavaDoc，实施者的产物核对不计作本轮重新验证 |

JDK 25 下在项目根目录执行，报告独立写入 `review-3`，没有覆盖 `review-2` 的原诊断或实施者结果：

```powershell
.\gradlew.bat --offline -I temp/verification/4.8.3-l/review-3/review.init.gradle componentSecondReviewDiagnostic componentSecondReviewRegression --continue -x webInstall --console=plain
```

证据：[隔离报告配置](../../../temp/verification/4.8.3-l/review-3/review.init.gradle)、[最终日志](../../../temp/verification/4.8.3-l/review-3/verification.log)、[原诊断复验报告](../../../temp/verification/4.8.3-l/review-3/diagnostic-report/index.html)、[65 项回归报告](../../../temp/verification/4.8.3-l/review-3/regression-report/index.html)。首次验证脚本误将相对路径用于 buildSrc，在测试启动前失败；修正附属构建的脚本作用域后按上表重跑，初次日志保留于 [run.log](../../../temp/verification/4.8.3-l/review-3/run.log)，不将脚本失败记为业务缺陷或测试通过。

## 后续

G03 不再放入待实施清单；已关闭范围见 [completed.md](completed.md)，真实容量和历史架构待复核事项仍见 [next-steps.md](next-steps.md)。本次结果不意味着已完成真实部署，也不改变原历史失败证据。
