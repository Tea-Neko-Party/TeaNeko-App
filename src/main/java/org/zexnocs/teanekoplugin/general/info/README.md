# 用户资料 Agent 工具

资料和留言继续由本包原业务服务读取，复用现有 EasyData，不使用 Agent 记忆表，不直接操作 Repository，也不发送平台消息。`message` 指留言板中该 UUID 收到的留言，不是聊天消息历史。

插件 ID 为 `general.info`，能力 ID 为 `general.info:user-info`，依赖 `teaneko.agent`。默认 Agent 实例自动安装；显式声明能力集合的实例须加入此能力，并允许 `AgentActivity.TOOL`。工具不指定 client 白名单，支持全部客户端；Agent 自身的 scope 登记、开关和轮次检查仍然生效。

| 工具 | 参数 | 返回 |
| --- | --- | --- |
| `teaneko_user_info` | 必填 `uuid`，完整 TeaNeko UUID | `uuid`、`personalInfo` 简介、`coinCount` 猫猫币、`randomMessage` 一条随机公开留言；没有留言时为 null。 |
| `teaneko_user_messages` | 必填 `uuid`；可选 `offset` 默认 0、范围 0–10000；`limit` 默认 5、范围 1–10 | `messages` 中每项包含 `senderUuid`、`message`、`createdAt`，另含 `offset`、`nextOffset` 和 `hasMore`。 |

示例参数：

```json
{"uuid":"00000000-0000-4000-8000-000000000001"}
```

```json
{"uuid":"00000000-0000-4000-8000-000000000001","offset":0,"limit":5}
```

公开资料对应既有 `/info` 中保存的简介、猫猫币和随机留言，不查询实时平台昵称、群成员身份或 UUID 与平台账号的对应关系，也不以空资料证明用户已经注册。需要平台来源资料时使用相应客户端能力。

完整留言列表沿用 `/留言 admin-get` 的 DEBUG 权限：仅在当前请求持有真实原生 DEBUG 来源时对模型展示，并在实际读取前后重新检查。普通用户、系统任务或缺少来源的内部调用不能通过 JSON 自报权限或复用过期工具绑定读取完整列表。公开资料中的一条随机留言保持原公开展示语义。

工具使用注解定义方法、参数说明和分页默认值，实际 Spring Bean 由原能力拥有。框架在执行前后额外复核 DEBUG 条件；条件撤销会停止当前交互，禁止交付已经读取的留言。参数转换失败使用固定工具失败状态，不返回原解析异常。

留言按时间倒序及留言者 UUID 稳定排序，分页读取既有服务返回的当前列表，不承诺跨页并发快照或新增数据库分页保证。每个留言者对同一目标的记录沿用原覆盖语义，不包含修改审计历史。返回超过工具字符预算时提供完整错误信封，可减小分页大小重试；不会截断 JSON 或暴露原异常。

通过 `tea-neko.plugins.disabled` 加入 `general.info` 可停用新增 Agent 工具能力；此插件声明不改变原 `/info`、个人简介及留言命令的归属和行为。
