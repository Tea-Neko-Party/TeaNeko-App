# OneBot Agent 工具与上下文

宿主默认通过 `teaneko.agent:onebot` 能力接入。只有 client 为 `onebot`、实例允许 TOOL 且当前请求持有原生来源租约时，才会向模型展示工具；普通内部服务或系统任务不会借用最近聊天者的身份。

提供者已采用类和方法注解生成 schema；类级 client 限定 OneBot，方法条件在选择及执行前后复核来源。消息查询直接返回 `CompletionStage<LLMToolOutput>`，框架统一处理图像协议，见 [注解工具开发](../../../../../../../../docs/development/plugins/agent-tools.md)。

| 工具 | 参数与行为 |
| --- | --- |
| `onebot_get_message` | 必填 `messageId`：查询当前 scope 消息的文本、图片、发送者上下文和时间；当前原生消息可直接读取，其他消息使用原消息发送器补取。图片显式送入下一次模型请求的视觉内容块，不只返回文字链接。 |
| `onebot_sender_info` | 可选 `target`：`sender` 为当前发送者，默认；`replied` 为当前原生引用消息的发送者。返回 TeaNeko UUID、QQ、昵称、群名片、头像 URL、scope；群聊另含群号和群名称。 |
| `onebot_group_info` | 无参数，查询当前群的名称、群号、成员数和容量；私聊明确返回无当前群。 |

同一能力在每轮准备时补充当前群资料，使用 `get_group_info`，不读取机器人加入的全部群。查询有等待上限，失败仅标记资料不可用；显示字段不决定权限。昵称和头像 URL 来自当前消息或已有 OneBot 工具箱，头像地址不表示已下载或验证图片存在。协议依据为 [OneBot 11 API](https://github.com/botuniverse/onebot-11/blob/master/api/public.md)。

模型上下文中的 `replyMessageIds` 提供至多四个原生引用 ID，`mentionedUserIds` 提供至多十六个原生提及 ID。模型可据此查询被引用消息；正文中自行声称的 ID 不成为原生引用或授权。

客户端、操作者和 scope 只来自宿主来源，模型参数不能更换。补取消息必须匹配请求 ID、客户端和 scope；OneBot 实现没有提供足够群来源字段，或私聊无法确认所属会话时明确拒绝，不猜测归属。消息查询发送器已保留顶层 `group_id` 并沿用原 scope 构造规则。

工具仅投影需要的数据，JSON 不包含整个客户端、权限对象或原平台异常。消息查询的文字信封包含 `imageCount`，图片通过显式结构化结果独立回填；过长的消息文字可标记省略并保留图片，图像负载不占工具八千字符文本上限。仍限制每次至多四张图、总引用长度 2097152 字符，只接受原有 HTTP(S)/data 格式，不下载或读取本地文件。所选模型须支持 USER 图像输入，仍经过原能力检查和模型预算，不自动换模型。

一批工具的 TOOL 结果全部配对后，交互层追加与调用 ID 关联的图像参考 USER 消息，再调用模型。这些参考不是用户新发言；Agent 的历史投影严格核对实际工具图像后将它们移除，不沉积到用户历史、记忆或后续窗口。其他查询输出超过工具文本预算时返回完整容量说明。资料和工具等待受轮次控制，底层发送任务继续由原 Sender 生命周期管理；超时、失败和来源消失不重试整个 Agent 轮次。

真实平台仍需验证 `get_msg`、`get_group_info`、`get_group_member_info` 的实现差异和权限。离线测试不调用 OneBot 或真实模型。
