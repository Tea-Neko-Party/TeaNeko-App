# 审查文档索引

审查按评估版本放在 `docs/review/{version}/`，版本目录不代表已经发布。实现器先读对应版本的下一步文档；已完成项和历史证据分开维护，历史报告中的建议不自动成为当前实施任务。

| 版本 | 阅读入口 | 用途 |
| --- | --- | --- |
| 4.9.1-l | [环境验收](4.9.1-l/next-steps.md) · [原生代理](4.9.1-l/antigravity.md) · [版本记录](4.9.1-l/README.md) | Gemini 原生适配与 Schema 实测完成，74 项本轮定向回归及实际产物核对；前轮结构纠错、运行边界、视觉模型与控制台证据保留 |
| 4.9.0-l | [环境验收](4.9.0-l/next-steps.md) · [日志与输入验证](4.9.0-l/llm-observation.md) · [结构复核](4.9.0-l/extension-structure.md) · [版本索引](4.9.0-l/README.md) | 模块化、扩展声明与配置职责重构完成，1443 项最新隔离回归及实际产物核对；真实生产环境单列 |
| 4.8.7-l | [剩余验收](4.8.7-l/next-steps.md) · [验证记录](4.8.7-l/verification.md) · [版本索引](4.8.7-l/README.md) | 审查确认项修复、真实 MySQL 升级/恢复及运行边界；模型、平台和长时验收单列 |
| 4.8.6-l | [后续入口](4.8.6-l/next-steps.md) · [评估标准](4.8.6-l/assessment-standard.md) · [完整索引](4.8.6-l/README.md) | 单实例受控用户评估基线；原问题编号与 v4.8.7-l 关闭范围可追溯 |
| 4.8.5-l | [下一步实施](4.8.5-l/next-steps.md) · [复核依据](4.8.5-l/review.md) · [已完成项](4.8.5-l/completed.md) | O01/O02 已修正，135 项定向回归与 4 项原诊断通过；实机验收另行安排 |
| 4.8.4-l | [下一步实施](4.8.4-l/next-steps.md) · [修正独立复核](4.8.4-l/reassessment.md) · [整体架构评估](4.8.4-l/architecture-review.md) | W01/A01/D02 已复核关闭；保留 Agent／Web 评估、条件设计及剩余验收边界 |
| 4.8.3-l | [G03 独立复评](4.8.3-l/g03-reassessment.md) · [剩余验收](4.8.3-l/next-steps.md) · [版本索引](4.8.3-l/README.md) | 组件修正与 G03 已关闭范围、历史证据及单独安排的实机验收 |
| 4.8.1-l | [整体架构评估基线](4.8.1-l/project-extensibility-maintainability-async-review.md) | 保留原版本的扩展性、维护性和异步评估，不冒充当前复评 |

实施计划仍归入 [docs/plan](../plan/README.md)。审查发现、实施完成与复评关闭是不同状态：修复后补实际验证证据，再将对应范围从下一步文档移入已完成项，保留原编号。
