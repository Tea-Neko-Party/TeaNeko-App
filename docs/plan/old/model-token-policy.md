# 模型 token 与上下文策略统一

状态：约定实现与离线验证完成，100%。

版本归属：[v4.7.2-l（待发布）](../../changelog/4.7.2-l.md)，与本次记忆修复及网页聊天入口改进一并交付。

- 输出额度统一继承所选模型的 `max-tokens`，移除业务常量与预算层自动缩减。
- 每模型声明 `context-window-tokens`；`context-input-ratio` 默认 0.8，同时为完整输出额度保留空间。
- 取消隐含固定总 token 配额，保留管理员显式预算、模型调用次数、时限、输入及领域数据容量。
- 验证配置与快照、上下文边界、工具循环增长、父子预算拒绝、后台记忆及其他生成调用；强制构建并核对产物。

验证：全量 1110 项测试中 1109 项通过、1 项跳过，无失败；38 个修改源文件通过 JavaDoc 检查。强制重新编译并核对可执行与普通 JAR 的全部项目类和前端资源。详细证据保存在工作区 `temp/model-token-*.log` 与 `temp/model-token-verification.json`。

部署边界：已有模型条目须补充真实上下文容量及正数输出额度；没有修改运行凭据或调用真实供应商模型。配置及使用规则见[模型配置](../../../src/main/java/org/zexnocs/teanekoagent/component/llm/file_config/README.md)。
