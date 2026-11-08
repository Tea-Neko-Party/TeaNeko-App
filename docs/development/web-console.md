# Web 控制台部署与开发

当前开发版本：4.9.0-l。已提供登录/注销、会话管理、概览、客户端目录、聊天配置、[Agent 工作区](web-console-agent.md)、[本地文件配置](web-console-files.md)、[模型配置](web-console-models.md)、[任务与日志](web-console-runtime.md)、[客户端指令台](web-console-commands.md)、指令一览及[独立网页聊天](web-console-chat.md)。基础功能见[归档记录](../plan/old/4.7.0-web-console.md)，模型与聊天补丁见[实施记录](../plan/old/4.8.2-web-model-chat.md)。

## 插件管理

左栏“插件管理”集中展示已装配插件的功能、ID、版本、依赖、客户端要求及当前状态。可跳转插件的本地配置、聊天配置或独立页面；“插件配置”二级菜单只列当前启用且有权限访问的专页。配置、组件、工具、命令、客户端和任务目录展示所属 pluginId 并支持筛选。

开关作用于整个实例，确认前展示受影响插件及阻塞原因。热启停完成后立即撤销相关页面与编辑器，插件总管理保留关闭项；历史数据和文件配置不删除。明确需要重启的插件才提示重启。网络中断时保存原操作编号并查询回执，不重新生成编号猜测结果。启用意愿保存在 `config/plugins.yml`。登录后默认开放全部已注册管理资源，旧配置无需补齐插件管理键。需要限制时设置 `local-allowlist-enabled: true`，此时下列三个白名单按精确键生效。迁移与扩展契约见[插件管理指南](plugins/management.md#插件总管理与升级)。

## 界面与模式

浅色背景为 `#F5F3ED`。登录页只需填写管理员令牌；“令牌在哪里？”可展开帮助。桌面侧栏可收起，手机通过左上角按钮打开导航。

右上角选择“简洁 / 高级”，默认简洁。常用 Agent 字段优先显示，其余可通过“展开其余配置”查看。聊天配置的名称保持简短，完整配置说明、字段说明与默认值直接展示；尚未登记时只提供登记按钮，登记成功后才显示字段。高级模式提供注销、文件字段特殊编辑和完整诊断信息。切换模式不清除草稿、筛选或待确认请求，已生效的详细日志筛选仍显示提示。

本地配置的普通字段可直接修改，恢复原文即撤销该字段变更；秘密字段须明确选择替换或清除。保存到磁盘后仍需单独应用，原操作结果不明时先查询回执。

聊天配置的“其他功能配置”可在配置选择框输入名称筛选。已登记项前面显示绿色圆点并优先排列，未登记项没有标记；绿色表示该配置已登记，不表示其中的功能开关已开启。登记、注销后立即更新，手动“更新已知目录”也会同步当前聊天的登记状态。

主题支持跟随系统、浅色和深色，点击主题按钮依次切换。主题、界面模式和桌面侧栏偏好保存在当前浏览器，和登录令牌分开；浏览器禁用存储时仍可临时切换。

右上角“外观设置”提供网页背景、前景文字和强调色的拾色器及十六进制输入。先在弹窗预览，保存后立即应用并保存在当前浏览器；面板、边框及按钮颜色随之派生。“恢复默认配色”继续使用原浅色、深色或系统主题，不修改服务端配置。刷新和更新目录使用统一图标，悬停提示与无障碍标签保留完整动作名称。

概览中的客户端详情卡片独立展开，同一行其他卡片不随之拉高。Agent 工作区通过返回图标切换聊天。聊天配置使用可展开卡片，每张卡片独立登记、应用修改或取消登记；切换卡片保留各自草稿与原操作编号。网页聊天的“聊天设置”和“活动与记忆”直接携带当前会话，切换目标仍须处理已有草稿或待确认操作。

JSON 和长文本字段可打开独立编辑弹窗，支持查找、撤销和确认/取消；JSON 还提供格式化及错误定位。确认只更新表单草稿，原有保存、应用和版本检查不变。编辑器声明与扩展见[管理扩展指南](plugins/management.md#值编辑器)。

页面首次进入时加载，切换侧栏保留当前草稿和原操作编号，并停止隐藏页轮询。主动退出前检查全部已打开页面；注销失败仍可返回原页处理。新增页面的声明和生命周期见[页面扩展指南](web-console-pages.md)。

## 4.8.4-l 升级配置

新安装的模板已包含独立空间与观测入口。旧的 `config/web/security.yml` 不自动改写；需要这些功能时，在原列表追加以下标识，重载后重新登录：

| 配置列表 | 追加内容 |
| --- | --- |
| `file-sections` | `agent.observability` |
| `management-resources` | `teaneko.agent:library`、`agent.cognition:memory-space`、`agent.cognition:personality-space`、`agent.cognition:profile-space`、`agent.cognition:layers-space` |

默认模式不限制指令台前缀，只展示并执行已声明管理来源的方法。启用 `local-allowlist-enabled` 后才按 `console-prefixes` 收窄，显式空列表禁用指令台。

## 构建与入口

概览机器状态卡片显示系统/程序 CPU、物理内存、JVM 堆用量和程序所在磁盘容量。页面可见时每 5 秒更新，切换页面、隐藏或退出时停止请求。首次 CPU 建立基准，无法提供的值显示暂不可用；详细口径见[机器指标](web-console-runtime.md#机器指标)。

构建与生产运行命令见[构建指南](build-and-run.md)。构建需要 JDK 25、Node 22.12 或以上版本及 npm；运行已打包的生产 JAR 不需要 Node。前端使用 Vue 3、TypeScript 6、Vite 和 Element Plus，依赖由锁文件固定。

```powershell
.\gradlew.bat bootJar
```

processResources 依赖 webBuild，后者按锁文件 npm ci、类型检查和生产构建；静态资源进入 JAR 的 static/console。资源处理会同步控制台生成目录，移除旧哈希分包，不清理其他应用资源。只改前端时可运行 `.\gradlew.bat webBuild`。前端没有独立应用版本来源，页面从后端读取 version.properties 对应版本。

4.7.1-l 起，Gradle 同时通过 `generateVersionInfo` 生成运行版本资源；JAR 优先读取清单，普通类路径读取生成资源。IntelliJ 使用 Gradle 构建即可；若使用 IDE 直接编译，先执行 `.\gradlew.bat generateVersionInfo` 并同步 Gradle。界面显示实际构建版本，修改源码的版本文件后需重新构建；`app.version` 或 `-Dversion` 不再覆盖它。实际部署版本切换仍未提供。

入口为 `https://你的域名/console/`。静态登录页面可公开，但 API、日志/配置猜测路径及未知入口默认拒绝。ManagementGateway 向已登录管理员默认开放已注册的聊天与实例资源；可选本地白名单用于进一步收窄。请求仍验证真实会话、CSRF、目标范围及插件资格。

## 首次启用

模板默认 `enabled: true`，默认 `prod` 由后端提供打包网页，不启动 Vite；已有配置不自动覆盖。生产部署需设置正式 HTTPS 来源及实际可信代理，并限制后端端口不被公网直接访问。显式关闭控制台后，Web API 返回控制台未启用。

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
# 默认开放已注册能力；设为 true 后下面三个列表才生效。
local-allowlist-enabled: false
console-prefixes: ['/console']
file-sections: ['agent.main', 'agent.models', 'agent.personality', 'agent.expression', 'agent.stickers', 'agent.media', 'onebot.main', 'web.chat', 'core.runtime']
management-resources:
  - 'teaneko.core:plugins.catalog'
  - 'teaneko.core:plugins.preview'
  - 'teaneko.core:plugins.change'
  - 'teaneko.core:plugins.operation'
  - 'teaneko.core:plugins.pages'
  - 'teaneko.agent:activity'
  - 'agent.cognition:layers'
  - 'agent.cognition:memory'
  - 'agent.cognition:profiles'
  - 'agent.cognition:personality'
  - 'agent.automation:schedules'
  - 'teaneko.core:runtime'
  - 'teaneko.core:logs'
```

public-origin 必须是完整 HTTPS 来源，不带路径或末尾斜线。直接 TLS 可留空 trusted-proxies；反向代理只填真实传输地址，必须覆盖 X-Forwarded-Proto 和 X-Forwarded-Host，不能转发客户端自报的值。登录来源严格匹配，不能以不同域名或端口交替访问。转发 IP 不用于放宽登录限流。

管理员令牌须为三十二至二百五十六字符且不含空白。机器令牌允许 0～256 字符，非空值不得包含空白，也不能与管理员令牌相同；可以使用短值。明确配置 `onebot: ''` 或纯空白值表示该客户端允许任意令牌及缺省认证头，完全省略客户端则拒绝连接。安全配置不供普通导出；“客户端 → 机器令牌”仅允许管理员显式查看、局部修改和应用机器凭据，完整认证根仍在本机管理。详见[客户端与机器令牌](web-console-clients.md)。

没有配置管理员令牌时，本次实例生成一次四十三字符随机值，保存在 data/web/runtime-admin-token。仅服务账户能访问文件/目录；不能设置可靠权限时拒绝开放管理入口。重启生成新值，普通同值重载保留当前随机值；配置错误或环境变量展开失败不进入匿名模式。

2026/11/04：启用 Web 后，后端启动 INFO 日志输出网站地址、当前登录令牌和 `https://你的域名/console/#token=令牌` 快捷链接。启动登录信息不进入网页日志窗口；后端日志包含管理员凭据，应按凭据管理其访问权限。推荐使用片段形式；同时兼容 `?token=令牌`，查询形式可能被服务器或代理访问日志记录。

启用 Web 后，已注册 `/ws/{client}` 机器入口支持直接 WS 和代理 WSS，并满足该客户端的机器令牌策略，不能使用管理会话代替。内网 WS 不依赖回环 IP 或代理头判断同机，因此适用于 Docker 网桥；其可达范围由后端监听、端口发布和防火墙控制。非空令牌使用 Authorization Bearer 头，明确空白放行时可不提供该头。网页 API 仍要求 HTTPS。握手后每次实际收发继续复核，令牌变更或配置不可用会使原证明失效；已受理的业务不宣称回滚。

启动失败日志包含阶段及固定原因：`SECURITY_CONFIG_INVALID` 表示安全配置校验失败，`CREDENTIAL_PREPARATION_FAILED` 表示根凭据准备或受限文件写入失败，`TOKEN_FILE_READ` 表示启动提示读取失败。根据阶段检查 `config/web/security.yml`、环境变量及 `data/web` 权限，不通过异常原文导出秘密。

## 浏览器会话

根令牌只用于本次登录，成功后使用服务端会话。Cookie 名为 __Host-TeaNekoSession，Secure、HttpOnly、SameSite=Strict、Path=/，只接受 Cookie，不从 URL 恢复会话。登录轮换标识，退出在服务端撤销；每次 API 使用检查当前凭据代次及绝对期限，空闲期限由容器执行。

2026/11/05：服务端登录会话仅存在于当前进程，统一设置 `server.servlet.session.persistent=false`，覆盖 DevTools 的会话持久化默认值。正常重启或开发热重启后需重新登录，不读取旧 `SESSIONS.ser`；这不影响浏览器保存令牌，也不影响数据库中的聊天记录、Agent 记忆与配置。登录身份绑定本次凭据代次，不支持跨重启恢复。

浏览器先读取本会话 CSRF，再提交表单；登录后重新取得防伪值。链接令牌仅自动填入登录表单，页面立即移除当前地址中的令牌，不自动绕过登录。勾选“在此浏览器保存令牌”并成功登录后，将令牌保存在当前来源的 localStorage，下次访问自动填入；取消勾选并成功登录即可清除。随机令牌重启后需重新登录并保存。浏览器密码管理器也可通过密码自动填充识别该字段。API 继续拒绝不可信或重复 Origin、明文访问和未经信任的代理伪造；根登录与匿名会话创建分别有限流，未知路由及 Actuator 不直接公开。

首期是单管理令牌/单实例，没有多自然人角色和多副本共享会话。不要把独立随机凭据的多个副本直接放在同一控制台负载均衡后。

## 前端开发

2026/11/04：端口统一在 `src/main/resources/application.properties` 的 `server.port`（后端）和 `tea-neko.web.frontend.port`（本机 Vite）配置。两者不能相同。联合启动将 Spring 实际监听端口和前端配置传给 Node；独立 Vite 读取该文件中的十进制端口值，也支持 `TEANEKO_BACKEND_PORT`、`TEANEKO_FRONTEND_PORT` 环境变量覆盖。HTTP/WSS 目标及转发 Host 自动派生，不再手改代理端口。修改前端端口后，仍需将安全配置的 `public-origin` 和访问地址改为实际 HTTPS 来源。以下地址使用默认值。

先确保后端原有数据库及应用配置可启动，在安全配置中使用：

```yaml
enabled: true
public-origin: 'https://localhost:5173'
trusted-proxies: ['127.0.0.1']
```

机器客户端仍需另配 machine-tokens。同机 OneBot 可直接使用 `ws://127.0.0.1:6691/ws/onebot`；需要开发 WSS 时使用 `wss://localhost:5173/ws/onebot`，由 Vite 转发到后端，实际端口随 `tea-neko.web.frontend.port` 配置。两种传输均使用机器 Bearer 令牌。开发自签证书须被机器客户端信任；浏览器接受证书不代表 OneBot 客户端也会信任。正式公网部署使用受信任证书。

`6691` 默认提供 HTTP，不直接提供 TLS；不要连接 `wss://localhost:6691/ws/onebot`，否则客户端会报 `WRONG_VERSION_NUMBER`，后端会报 HTTP 方法包含 `0x16 0x03` 等无效字符。这是 TLS 握手被普通 HTTP 端口解析，发生在令牌验证之前。

### 开发连接排错

- 后端报告 HTTP 方法包含 `0x16 0x03`：连接方把 TLS 握手发送到了 HTTP 端口。本机可使用 `ws://127.0.0.1:6691/ws/onebot`，WSS 则连接提供 TLS 的实际代理端口，不能直接对 HTTP 后端使用 `wss://`。
- 客户端报告 `self signed certificate`：已经接入 TLS，但客户端尚未信任 Vite 的开发证书；该错误发生在 WebSocket 握手及令牌校验之前。

启动开发前端后，在项目根目录执行：

```powershell
npm --prefix frontend run dev:certificate
$env:NODE_EXTRA_CA_CERTS = (Resolve-Path 'frontend/node_modules/.vite/basic-ssl/teaneko-dev-ca.pem').Path
# 随后在同一窗口启动使用 Node.js 的 OneBot 客户端。
```

导出文件只包含公钥证书，不含私钥。原始 `_cert.pem` 同时包含私钥，不应交给客户端。`NODE_EXTRA_CA_CERTS` 只在 Node.js 进程启动时读取，浏览器接受证书或后端设置变量均不能改变另一个已运行客户端的信任状态。客户端使用独立证书配置时，导入导出的 PEM 文件；开发证书更新后重新导出并重启客户端。正式环境使用受信任证书的反向代理入口。

### 随后端自动启动前端

默认 `./gradlew bootRun` 使用 `prod`，网页由后端直接提供。本地联合开发须显式选择 `dev,web-dev`，工作目录设为项目根目录，并将安全配置设为本机 HTTPS 来源与可信开发代理。

在 IntelliJ 的后端运行配置中，将 Active profiles 设置为 `dev,web-dev`；也可在 VM options 设置 `-Dspring.profiles.active=dev,web-dev`，或在程序参数加入 `--spring.profiles.active=dev,web-dev`，三种方式任选一种并替换原有设置。不要将 `--spring.profiles.active=...` 放入 VM options。如果原来使用其他 Profile，将 `dev` 替换成原值。首次先在项目根目录执行：

```powershell
.\gradlew.bat webInstall
```

之后运行 IntelliJ 后端配置即可；Gradle 联合开发使用：

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

访问 https://localhost:5173/console/，接受仅本地开发使用的自签证书。Vite 仅绑定本机，将 `/api/` 和 `/ws/` 代理到统一配置的后端端口。生产不运行 Vite，使用正式 TLS 和 JAR 中的静态产物。

## 客户端扩展

客户端页显示机器入口的公开 WSS 地址、服务器本机 WS 地址及两者端口。Docker 内网使用后端服务名及 `server.port`。令牌默认遮盖，仅点击眼睛时通过受保护入口读取磁盘值；隐藏或离开页面即清除显示，下方可修改 Token。保存仍与应用分开，未应用的磁盘令牌不代表当前握手策略。

需要独立后端监听时设置 `tea-neko.websocket.port`（默认 `0` 共用 `server.port`），非零端口只绑定 `127.0.0.1`。联合开发启动自动将该端口交给 Vite 的 `/ws/` 代理；单独启动可用 `TEANEKO_WEBSOCKET_PORT` 覆盖。公开 WSS 端口独立于后端及开发前端端口，通过 TLS 代理实际监听，并设置 `tea-neko.websocket.public-origin=wss://主机:端口`；留空继承控制台 HTTPS 来源，见[自定义 WSS 端口](web-console-clients.md#自定义-wss-端口)。这些监听设置修改后需重启。

已有 Spring Bean 实现 IClientManagementAdapter 并标记 @TeaNekoClientManagement("实际clientId")；ClientManagementDirectory 复用 Core AnnotatedBeanRegistry 和 scanner，校验版本、重复键及返回标识。@PluginOwned 沿用原插件启用/代次，不因为扫描到实现就宣称当前账号可操作。

describe 返回中文平台名、状态、实际连接数、说明、观测时间和设置能力，不导出 session、scope 或凭据。账号目录、目标分页、服务端解析与显式目录同步分别由 accounts、targets、resolve、discover 扩展；指令台通过 consoleState 与 consoleLease 明确扩展，默认不支持。平台插件代次在实际写事务与返回时复核，目录发现不能代替授权。来源、固定连接及真实回执见[指令台说明](web-console-commands.md)。

机器连接策略由 IClientConnectionGuard 的 Spring Bean 贡献，WebSocketConfig 统一安装握手与 GuardedClientWebSocketHandler。所有策略均须通过；实际客户端保存的会话包装器也复核异步发送，不能只检查握手或入站。此端口不进入 Core，平台解析器不直接依赖 Web 凭据实现。

## 验证与实机待办

已完成管理认证、配置/文件事务、机器令牌、分层记忆、任务分类、机器指标和平台生命周期的定向回归，并通过前端类型/生产构建、本机浏览器夹具及实际 JAR 核对。完整范围和证据见[补丁验证记录](../plan/old/4.7.1-web-operations-validation.md)，不代替以下部署检查。

- [ ] 正式证书、可信代理头、后端端口保护、浏览器 Cookie 与空闲过期。
- [ ] 实际机器 WS/WSS 握手（同机、Docker 内网及 TLS 代理）、短值/空白/缺省策略、换密钥后旧连接拒绝及多连接行为。
- [ ] 服务账户文件权限、原子替换、环境变量；普通配置授权和 `core.runtime` 保存/应用。
- [ ] MySQL/JPA 上配置与网页聊天的并发、回执恢复和重启；真实模型视觉、分层记忆与逐段表达。
- [ ] 实际部署产物版本、CPU/内存口径及页面隐藏后的刷新停止。
- [ ] v4.7.7-l：同协议不同路由的真实凭据与模型选择、聊天状态与实际回复、重启后的聊天名称及群名片隔离；不将离线夹具结果当作供应商联调通过。
- [ ] v4.8.0-l：确认旧聊天能力保留、新登记为纯 LLM；演练安装/卸载记忆与人格、重启后装配还原、私有模型环境变量与真实供应商认证，以及组件参数修改后的在途请求撤销。升级前保留包含私有配置的受保护数据库备份。

默认网页记忆共享，实机演练前应明确测试 namespace。以上项目尚未执行，发布及部署仍单独确认。

## 聊天设置

v4.8.0-l 的 Agent 配置使用[组件装配表单](agent-composition.md)。新登记默认只有 LLM；旧记录沿用已有能力。组件与工具说明位于侧栏“API 文档”。

v4.7.6-l 会从所有适用于当前客户端、插件已启用的配置管理器恢复已登记聊天，不再只扫描 Agent 配置；按各管理器原 EasyData 存储及独立游标有界读取。导入旧数据库后，账号无法确认的配置进入“历史配置（账号未归属）”，群聊与私聊不会伪装成当前连接账号的联系人。私聊还需要原 TeaUser 平台映射可用。选择该历史目录即可编辑；配置较多时点击“继续发现已登记配置”，全部发现后“更新已知目录”可重新扫描后续迁移或新增记录。离线发现配置不要求 OneBot 连接，平台联系人查询仍要求真实连接。

进入“聊天配置”，依次选择平台、机器人账号或历史目录、群聊/私聊和目标。“Agent 配置”和“其他功能配置”分别管理；普通配置按原 `@ConfigManager` 自动发现，字段说明来自 `@Description`。没有说明时显示真实字段名。先选择配置类型，未登记时只预览默认值；点击“登记此配置”提交该类型默认值，再编辑字段。注销仅删除这一配置。

布尔、数字和枚举使用对应控件，集合与对象使用 JSON；字符串与空值分开，不把文字 `null` 当空值。保存仍经过原字段校验器和完整类型绑定。支持继承字段、泛型和实际序列化别名；敏感字段只显示受保护提示。命名空间、人格绑定等专用管理约束继续保留。

切换平台、账号、聊天或配置类型时，可保留当前草稿及待确认请求；返回时恢复原值与原版本，保存仍可能遇到版本冲突。草稿只在当前页面内存保留，刷新或退出会丢失；切换简洁/高级模式不会丢失。

目录合并真实消息元数据、已登记配置和明确请求的平台联系人查询，不保存聊天正文。无法确认账号的旧配置放在“历史配置（账号未归属）”，不猜测归属。目录存于 Agent 专用表的 agent.chat-directory.v1 域，每个平台至多 32 个账号、8192 个目标；页面每次至多加载 50 项，搜索仅筛选已加载名称与号码。

“更新已知目录”每次导入至多 100 项原配置。当前连接唯一且已从该连接的原始帧确认机器人账号时，才额外查询群列表和好友列表，每分钟至多一次、不重入，两类列表分别限制 2000 项，不发送聊天消息。离线、身份未确认或多连接时只更新本地目录；连接代次变化会拒绝迟到查询结果。目录不表示当前全部联系人，离线仍可编辑已确认映射的持久配置。

同一平台的多个账号可能共享同一群配置，同一 TeaNeko UUID 的私聊配置可能跨平台共享；界面会提示影响，不改变原隔离规则。记忆空间来自权威绑定端口，只读，切换仍走专用记忆管理。类型默认值和未保存预览不冒充全部运行时继承结果。

写入使用原 ConfigManager 声明的业务 EasyData 和字段校验器，Agent 仍使用专用表；只合并已开放的修改字段并保留未知字段。预期版本在持锁事务中检查，冲突返回 409，不自动覆盖。原 `/cfg` 字段写入也在事务内合并当前行；索引删除遇到快照变化时拒绝，防止旧缓存覆盖新值。

配置与操作回执同事务提交；会话、客户端适用范围及插件代次在事务中复核。回执只保存操作者/目标摘要、字段名、动作、时间与版本，不保存字段值。每管理器保留 24 小时、最多 512 项，窗口满拒绝新写入。当前本地管理员是同一个管理主体，尚无多自然人审计身份。

失败或断开不能证明未提交。页面保留原 operationId，可查询或原样重试；不同参数不能复用同一编号。可明确放弃草稿并回读当前值，这不会撤销已提交操作；保留期之外不能依赖原编号重试。

自 4.7.3-l 起，管理员可直接管理所有已启用且适用于当前客户端的聊天配置，无需先修改本地 `settings-sections` 或 `settings-groups`。旧字段可保留以读取既有文件，但不再参与聊天配置访问判断。客户端命名空间、插件状态、字段校验、版本检查及原业务事务继续生效；文件配置的 `file-sections` 和其他管理资源白名单保持独立。

| 已发布 HTTP 路由 | 用途 |
| --- | --- |
| GET /api/v1/clients/{client}/accounts | 已知账号或明确历史目录 |
| POST /api/v1/clients/{client}/actions/discover | 当前授权下刷新目录 |
| GET /api/v1/clients/{client}/accounts/{account}/targets | after、kind 过滤的目标页 |
| GET /api/v1/clients/{client}/accounts/{account}/targets/{target} | 已授权目标的最新显示名称，不导出身份资料或真实 scope |
| GET /api/v1/settings/sections | 当前允许的设置区 |
| GET /api/v1/clients/{client}/accounts/{account}/targets/{target}/settings | 当前真实聊天适用的配置区 |
| GET /api/v1/clients/{client}/accounts/{account}/targets/{target}/settings/{section} | 中文字段与实际版本 |
| POST 同一设置路径 | operationId、expectedRevision、action、fields；动作 REGISTER、PATCH、UNREGISTER |
| GET 同一设置路径/operations/{operationId} | 当前管理主体的原回执 |

浏览器只提交服务器发出的账号/目标引用，不接受 scope、存储类、操作者或 DEBUG 身份。全部 API 要求真实管理会话，写入额外要求 CSRF。插件扩展方式见[管理开发](plugins/management.md)。
