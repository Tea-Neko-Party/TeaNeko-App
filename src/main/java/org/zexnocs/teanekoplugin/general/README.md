# 通用业务插件

| 目录 | 插件 ID | 职责 |
| --- | --- | --- |
| `system` | `general.system` | 宿主状态、诊断与基础管理命令 |
| `games` | `general.games` | 骰子、真心话等轻量互动 |
| `info` | `general.info` | 公开信息查询 |
| `engagement` | `general.engagement` | 签到与好感度 |
| `group_management` | `general.group-management` | 群活跃度、机器人治理及主群设置 |
| `image_search` | `general.image-search` | 图片来源查询 |

每个分类注册一个插件入口，命令、配置和后台任务按同一插件 ID 展示与筛选。插件停用会关闭新入口并等待其拥有的在途任务完成，保存的配置和业务数据保留；重新启用建立新的任务生命周期，旧定时回调不能重新生效。
