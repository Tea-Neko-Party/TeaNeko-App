# 审查文档索引

审查按评估版本放在 `docs/review/{version}/`，版本目录不代表已经发布。实现器先读对应版本的下一步文档；已完成项和历史证据分开维护，历史报告中的建议不自动成为当前实施任务。

| 版本 | 阅读入口 | 用途 |
| --- | --- | --- |
| 4.8.6-l | [下一步实施](4.8.6-l/next-steps.md) · [评估标准](4.8.6-l/assessment-standard.md) · [完整索引](4.8.6-l/README.md) | 单实例受控用户 MVP；先复核旧问题，再评估整体架构、存储/API、UI 和 Java 规范；已完成与本轮证据单列 |
| 4.8.5-l | [下一步实施](4.8.5-l/next-steps.md) · [复核依据](4.8.5-l/review.md) · [已完成项](4.8.5-l/completed.md) | O01/O02 已修正，135 项定向回归与 4 项原诊断通过；实机验收另行安排 |
| 4.8.4-l | [下一步实施](4.8.4-l/next-steps.md) · [修正独立复核](4.8.4-l/reassessment.md) · [整体架构评估](4.8.4-l/architecture-review.md) | W01/A01/D02 已复核关闭；保留 Agent／Web 评估、条件设计及剩余验收边界 |
| 4.8.3-l | [G03 独立复评](4.8.3-l/g03-reassessment.md) · [剩余验收](4.8.3-l/next-steps.md) · [版本索引](4.8.3-l/README.md) | 组件修正与 G03 已关闭范围、历史证据及单独安排的实机验收 |
| 4.8.1-l | [整体架构评估基线](4.8.1-l/project-extensibility-maintainability-async-review.md) | 保留原版本的扩展性、维护性和异步评估，不冒充当前复评 |

实施计划仍归入 [docs/plan](../plan/README.md)。审查发现、实施完成与复评关闭是不同状态：修复后补实际验证证据，再将对应范围从下一步文档移入已完成项，保留原编号。
