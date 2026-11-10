# v4.9.1-l 配置资源与图片处理边界验收

2026/11/10 完成三个修复闭环，3/3；实际部署和远端模型验证未执行。

## 根因与实现

1. 配置模板原来通过 `ClassPathResource` 隐式使用请求线程的上下文类加载器。线程继承已停止的 Web 容器加载器时，模板存在性检查直接抛错。文档读取和首次模板复制现均绑定注册配置类型的类加载器，保留类型锁、扫描缓存、文件版本与原子发布。日志没有证据表明 `image-input` 缺省导致容器停止；本修复不把配置缺省和类加载器状态混为同一原因，也不声称已定位旧线程为何存活。
2. 本机 `config/agent/stickers.yml` 的 `max-file-bytes` 为 6291456（6 MiB），原下载第 261 行根据 Content-Length 拒绝。原行为把这种策略拒绝记成 `FAILED / PROCESSING_FAILED` 并输出通用 ERROR。现对声明超限、流式超限和内联大小超限使用独立类型，以 `REJECTED / FILE_TOO_LARGE` 持久收尾，INFO 给出实际证据和上限，继续原清理及预约释放流程，不进入模型分析。实际流只报告已读下界，不泄露链接或正文，不放宽文件限制。
3. `requireCapacity` 抛出预算异常，而该异常未声明可公开原因，安全诊断因此落入“未识别异常”。异常现实现既有 `IFailureReason`，保留资源维度、上限、已占用、剩余和本次需求；不读取任意外部异常正文。交互预检从同一快照取真实上限和占用量，避免分次读取把并发差值算成配置上限。旧日志不足以还原当时耗尽的是哪一维度，不能猜测为模型 token 或供应商限额；次数和总 token 预算继续独立生效。

## 验证

- 96 项定向回归通过，0 失败、0 错误、0 跳过；覆盖配置当前读与文件管理事务、素材下载与采集、备用视觉、LLM 交互与 token 预算、Core 资源预算、安全诊断及项目分层。
- 失效上下文加载器夹具验证文档预览和首次模板初始化均无需访问旧加载器，模板内容确实复制并发布。
- 下载测试覆盖 Content-Length 与无长度流两条路径的关闭、Base64 解码边界和编码预检；采集集成验证明确拒绝回执、清理完成、零预约、零视觉调用和无通用 ERROR。
- 实际交互夹具验证局部模型额度、父共享额度及工具批次拒绝的数字对照，额外模型请求和超限工具副作用均未发生。诊断保留异步根因，隐藏非法资源标识和包装原文。
- 76 个变更 Java 文件通过增量格式检查和 JavaDoc。Gradle 构建中前端类型检查与生产构建通过；本轮未修改前端，没有重复浏览器验收。`git diff --check` 通过。

完整日志与测试 XML：`temp/verification/4.9.1-l/runtime-boundaries/`；JavaDoc：`temp/javadoc/4.9.1-l/runtime-boundaries/`。

## 实际产物

`build/libs/TeaNeko-App-4.9.1-l.jar` 的清单与运行版本均为 4.9.1-l。2186 个 class、54 个前端资源与构建目录逐字节一致，无缺失或额外项；视觉测试图和插件模型模板均匹配源码资源。本轮没有改名或删除生产类型。

SHA-256：`d477a5ed8d96f66c2248267f63f8ebf78192a24c806a36ee8f8874927beba972`。保留用户的 `gradle.properties` 和告警阈值 163840；未更改实际模型或素材限制配置。

需要完整重启实例加载本轮代码。本轮没有启动真实应用、访问真实数据库或客户端，也没有调用真实模型。后续操作见[环境验收](next-steps.md)，行为说明见[文件配置](../../../src/main/java/org/zexnocs/teanekocore/file_config/README.md)、[素材采集](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/README.md)和[交互预算](../../../src/main/java/org/zexnocs/teanekoagent/llm/framework/interaction/README.md)。
