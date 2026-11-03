# Web 控制台部署与开发

当前开发版本：4.7.0-l。已提供登录/注销、会话管理、概览、客户端目录、聊天配置、[Agent 工作区](web-console-agent.md)、[本地文件配置](web-console-files.md)、[任务与日志](web-console-runtime.md)、[客户端指令台](web-console-commands.md)及[独立网页聊天](web-console-chat.md)。交付记录见[归档计划](../plan/old/4.7.0-web-console.md)。

## 构建与入口

构建需要 JDK 25、Node 22.12 或以上版本及 npm；运行已打包 JAR 不需要 Node。前端使用 Vue 3、TypeScript 6、Vite 和 Element Plus，锁文件固定经过核验的组合；当前 Vue 类型检查工具未兼容 TypeScript 7，不能仅升级该依赖就假设构建可用。

```powershell
.\gradlew.bat bootJar
```

processResources 依赖 webBuild，后者按锁文件 npm ci、类型检查和生产构建；静态资源进入 JAR 的 static/console。只改前端时可运行 `.\gradlew.bat webBuild`。前端没有独立应用版本来源，页面从后端读取 version.properties 对应版本。

入口为 `https://你的域名/console/`。静态登录页面可公开，但 API、日志/配置猜测路径及未知入口默认拒绝。ManagementGateway 仅公开本地白名单中的聊天与实例资源，仍保持原显式授权要求。

## 首次启用

默认 enabled=false，Web API 返回控制台未启用；已有机器接入保持原行为。开启前先准备 HTTPS，并限制后端端口不被公网直接访问。

在本地 config/web/security.yml 配置：

```yaml
enabled: true
public-origin: 'https://console.example.com'
admin-token: '${TEANEKO_WEB_ADMIN_TOKEN:}'
idle-minutes: 30
absolute-hours: 8
trusted-proxies:
  - '127.0.0.1'
machine-tokens:
  onebot: '${TEANEKO_ONEBOT_ACCESS_TOKEN}'
settings-sections: ['agent']
console-prefixes: ['/console']
file-sections: ['agent.main', 'agent.models', 'agent.personality', 'agent.expression', 'agent.stickers', 'agent.media', 'onebot.main', 'web.chat']
management-resources:
  - 'teaneko.agent:activity'
  - 'teaneko.agent:layers'
  - 'teaneko.agent:memory'
  - 'teaneko.agent:profiles'
  - 'teaneko.agent:personality'
  - 'teaneko.agent:schedules'
  - 'teaneko.core:runtime'
  - 'teaneko.core:logs'
```

public-origin 必须是完整 HTTPS 来源，不带路径或末尾斜线。直接 TLS 可留空 trusted-proxies；反向代理只填真实传输地址，必须覆盖 X-Forwarded-Proto 和 X-Forwarded-Host，不能转发客户端自报的值。登录来源严格匹配，不能以不同域名或端口交替访问。转发 IP 不用于放宽登录限流。

管理员令牌及机器令牌至少三十二字符，建议使用至少三十二字节随机秘密；长度不能证明随机性。两类令牌必须不同。安全配置标为敏感，凭据不供普通序列化导出，没有网页修改认证根配置的入口。

没有配置管理员令牌时，本次实例生成一次四十三字符随机值，保存在 data/web/runtime-admin-token。仅服务账户能访问文件/目录；不能设置可靠权限时拒绝开放管理入口。重启生成新值，普通同值重载保留当前随机值；配置错误或环境变量展开失败不进入匿名模式。

2026/11/04：启用 Web 后，后端启动 INFO 日志输出网站地址、当前登录令牌和 `https://你的域名/console/#token=令牌` 快捷链接。启动登录信息不进入网页日志窗口；后端日志包含管理员凭据，应按凭据管理其访问权限。推荐使用片段形式；同时兼容 `?token=令牌`，查询形式可能被服务器或代理访问日志记录。

启用 Web 后，所有已注册 /ws/{client} 机器握手要求各自配置的 Bearer 凭据和 HTTPS/WSS，不能使用管理会话代替。OneBot 实现须配置对应反向 WSS 地址和 Authorization 凭据；未配置则拒绝连接。握手后每次实际收发继续复核，密钥变更、启用 Web 或配置不可用会停止旧连接的后续收发；已经受理的业务不会被宣称回滚。关闭 Web 是显式恢复旧机器接入方式，不应在公网以此代替正确配置凭据。

## 浏览器会话

根令牌只用于本次登录，成功后使用服务端会话。Cookie 名为 __Host-TeaNekoSession，Secure、HttpOnly、SameSite=Strict、Path=/，只接受 Cookie，不从 URL 恢复会话。登录轮换标识，退出在服务端撤销；每次 API 使用检查当前凭据代次及绝对期限，空闲期限由容器执行。

浏览器先读取本会话 CSRF，再提交表单；登录后重新取得防伪值。链接令牌仅自动填入登录表单，页面立即移除当前地址中的令牌，不自动绕过登录。勾选“在此浏览器保存令牌”并成功登录后，将令牌保存在当前来源的 localStorage，下次访问自动填入；取消勾选并成功登录即可清除。随机令牌重启后需重新登录并保存。浏览器密码管理器也可通过密码自动填充识别该字段。API 继续拒绝不可信或重复 Origin、明文访问和未经信任的代理伪造；根登录与匿名会话创建分别有限流，未知路由及 Actuator 不直接公开。

首期是单管理令牌/单实例，没有多自然人角色和多副本共享会话。不要把独立随机凭据的多个副本直接放在同一控制台负载均衡后。

## 前端开发

先确保后端原有数据库及应用配置可启动，在安全配置中使用：

```yaml
enabled: true
public-origin: 'https://localhost:5173'
trusted-proxies: ['127.0.0.1']
```

机器客户端仍需另配 machine-tokens。

### 随后端自动启动前端

2026/11/04：本地开发可额外启用 `web-dev` Profile。保持原有后端 Profile，并将应用工作目录设为项目根目录；已配置的 Web 认证和 HTTPS 来源仍按上文要求准备。

在 IntelliJ 的后端运行配置中，将 Active profiles 设置为 `dev,web-dev`；也可在 VM options 设置 `-Dspring.profiles.active=dev,web-dev`，或在程序参数加入 `--spring.profiles.active=dev,web-dev`，三种方式任选一种并替换原有设置。不要将 `--spring.profiles.active=...` 放入 VM options。如果原来使用其他 Profile，将 `dev` 替换成原值。首次先在项目根目录执行：

```powershell
.\gradlew.bat webInstall
```

之后直接运行后端即可。使用 Gradle 联合启动时执行：

```powershell
.\gradlew.bat bootRun "-PspringProfile=dev,web-dev"
```

后端就绪后直接调用 PATH 中的 Node 启动 Vite，前端输出进入同一终端，访问地址仍为 `https://localhost:5173/console/`。正常关闭或 DevTools 热重启会终止本实例启动的前端及子进程；已有手动启动的 Vite 须先关闭，端口冲突时不会接管它。系统强制结束 JVM 时不保证执行关闭回调。

仅启用 Web 或使用普通 Profile 不会启动 Node。正式部署通过 `bootJar` 内的静态资源提供前端，仍使用正式 HTTPS。

### 单独启动前端

需要独立控制前端时，不添加 `web-dev`，启动后端后在 frontend 目录执行：

```powershell
npm ci
npm run dev
```

访问 https://localhost:5173/console/，接受仅本地开发使用的自签证书。Vite 仅绑定本机，将 /api/ 同源代理到 127.0.0.1:6691；改变后端端口时同步修改开发代理。生产不运行 Vite，使用正式 TLS 和 JAR 中的静态产物。

## 客户端扩展

已有 Spring Bean 实现 IClientManagementAdapter 并标记 @TeaNekoClientManagement("实际clientId")；ClientManagementDirectory 复用 Core AnnotatedBeanRegistry 和 scanner，校验版本、重复键及返回标识。@PluginOwned 沿用原插件启用/代次，不因为扫描到实现就宣称当前账号可操作。

describe 返回中文平台名、状态、实际连接数、说明、观测时间和设置能力，不导出 session、scope 或凭据。账号目录、目标分页、服务端解析与显式目录同步分别由 accounts、targets、resolve、discover 扩展；指令台通过 consoleState 与 consoleLease 明确扩展，默认不支持。平台插件代次在实际写事务与返回时复核，目录发现不能代替授权。来源、固定连接及真实回执见[指令台说明](web-console-commands.md)。

机器连接策略由 IClientConnectionGuard 的 Spring Bean 贡献，WebSocketConfig 统一安装握手与 GuardedClientWebSocketHandler。所有策略均须通过；实际客户端保存的会话包装器也复核异步发送，不能只检查握手或入站。此端口不进入 Core，平台解析器不直接依赖 Web 凭据实现。

## 验证与实机待办

首批只新增三项核心安全测试，使用实际 Spring Security/MVC、会话及 CSRF，覆盖认证、来源、未知入口、轮换/期限、私有随机文件、机器独立认证与持续收发拒绝；复用原 OneBot 生命周期用例。前端执行严格类型检查和生产构建；页面布局另用本机浏览器及离线 API 契约夹具核验，不将其称为真实后端端到端验收。

仍需操作者在部署环境验证正式证书、可信代理头与后端端口保护、真实浏览器 Cookie/空闲过期、机器 WSS 握手及换密钥、多连接实际行为，以及 MySQL/JPA 上网页聊天的并发/重启、原模型视觉和逐段表达。默认网页记忆共享时，实机演练前应明确测试 namespace。不启动真实机器人、模型或 MySQL 来完成离线开发，也不把离线通过表示为这些实机项目已通过。

## 聊天设置

进入“聊天设置”，依次选择 OneBot、机器人账号或历史目录、群聊/私聊和目标。字段按中文职责分组，展示当前值、类型默认值、来源与共享影响。未登记时只预览默认值；“登记并启用 Agent”明确提交默认值，再编辑字段。注销只删除配置，不删除记忆、人格、历史或图库。

目录合并真实消息元数据、已登记配置和明确请求的平台联系人查询，不保存聊天正文。无法确认账号的旧配置放在“历史配置（账号未归属）”，不猜测归属。目录存于 Agent 专用表的 agent.chat-directory.v1 域，每个平台至多 32 个账号、8192 个目标；页面每次至多加载 50 项，搜索仅筛选已加载名称与号码。

“更新已知目录”每次导入至多 100 项原配置。当前连接唯一且已从该连接的原始帧确认机器人账号时，才额外查询群列表和好友列表，每分钟至多一次、不重入，两类列表分别限制 2000 项，不发送聊天消息。离线、身份未确认或多连接时只更新本地目录；连接代次变化会拒绝迟到查询结果。目录不表示当前全部联系人，离线仍可编辑已确认映射的持久配置。

同一平台的多个账号可能共享同一群配置，同一 TeaNeko UUID 的私聊配置可能跨平台共享；界面会提示影响，不改变原隔离规则。记忆空间来自权威绑定端口，只读，切换仍走专用记忆管理。类型默认值和未保存预览不冒充全部运行时继承结果。

写入使用原 ConfigManager、Agent 专用 EasyData 和字段校验器，只合并已开放的修改字段并保留未知字段。预期版本在持锁事务中检查，冲突返回 409，不自动覆盖。原 `/cfg` 字段写入也在事务内合并当前行；索引删除遇到快照变化时拒绝，防止旧缓存覆盖新值。

配置与操作回执同事务提交；会话、白名单及插件代次在事务中复核。回执只保存操作者/目标摘要、字段名、动作、时间与版本，不保存字段值。每管理器保留 24 小时、最多 512 项，窗口满拒绝新写入。当前本地管理员是同一个管理主体，尚无多自然人审计身份。

失败或断开不能证明未提交。页面保留原 operationId，可查询或原样重试；不同参数不能复用同一编号。可明确放弃草稿并回读当前值，这不会撤销已提交操作；保留期之外不能依赖原编号重试。settings-sections 默认只开放 agent，变更白名单须修改本地安全配置。

| 已发布 HTTP 路由 | 用途 |
| --- | --- |
| GET /api/v1/clients/{client}/accounts | 已知账号或明确历史目录 |
| POST /api/v1/clients/{client}/actions/discover | 当前授权下刷新目录 |
| GET /api/v1/clients/{client}/accounts/{account}/targets | after、kind 过滤的目标页 |
| GET /api/v1/settings/sections | 当前允许的设置区 |
| GET /api/v1/clients/{client}/accounts/{account}/targets/{target}/settings/{section} | 中文字段与实际版本 |
| POST 同一设置路径 | operationId、expectedRevision、action、fields；动作 REGISTER、PATCH、UNREGISTER |
| GET 同一设置路径/operations/{operationId} | 当前管理主体的原回执 |

浏览器只提交服务器发出的账号/目标引用，不接受 scope、存储类、操作者或 DEBUG 身份。全部 API 要求真实管理会话，写入额外要求 CSRF。插件扩展方式见[管理开发](plugins/management.md)。
