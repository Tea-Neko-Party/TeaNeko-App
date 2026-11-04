<p align="center">
<img src="docs/img/cover.png" alt="TeaNeko-App Cover" width="1441" />
<br>

<!-- 项目信息 -->
<img src="https://img.shields.io/github/license/Tea-Neko-Party/TeaNeko-App" alt="License">
<img src="https://img.shields.io/github/repo-size/Tea-Neko-Party/TeaNeko-App" alt="Repo Size">
<img src="https://img.shields.io/github/v/tag/Tea-Neko-Party/TeaNeko-App?style=flat-square" alt="Version">

<!-- 技术栈 -->
<img src="https://img.shields.io/badge/Java-25-orange" alt="Java 25">
<img src="https://img.shields.io/badge/Spring_Boot-4.1.2-brightgreen" alt="Spring Boot 4.1.2">
<img src="https://img.shields.io/badge/Gradle-9.8.0-02303A" alt="Gradle 9.8.0">

</p>

TeaNeko App 是专注于多平台聊天机器人的架构，允许扩展平台适配器和插件。Agent 已提供图文对话、记忆与人格、后台摘要/补正、原权限命令工具、Skill、提醒任务、自主候选及内部异步客户端；统一入口见 [Agent 功能说明](src/main/java/org/zexnocs/teanekoagent/README.md)，首次部署和实机验收见 [Agent 部署说明](docs/development/agent-deployment.md)。

---

## 🚀 构建说明
| 步骤 | 说明                                      | bash                                                          |
|:--:|-----------------------------------------|---------------------------------------------------------------|
| 1  | - 安装 Java 25<br> - 安装 Node.js 22.12 或以上版本及 npm（源码构建和前端开发需要）<br> - 安装任意兼容 `JDBC` 的数据库环境 |                                                               |
| 2  | 克隆该项目                                   | `git clone https://github.com/Tea-Neko-Party/TeaNeko-App.git` |
| 3  | 配置数据库连接信息                               | 位于 `src/main/resources/application-dev.properties`            |
| 4  | 构建项目                                    | `./gradlew build -x test`                                     |
| 5  | 复制项目样板配置                                | `./gradlew copyTemplatesToConfig`                             |
| 6  | 根据注释配置 `config` 目录下配置文件                 |                                                               |
| 7  | 使用开发配置启动项目                            | `./gradlew bootRun -PspringProfile=dev`                        |

---

版本升级、更新日志及独立 Agent Git 提交入口见 [版本操作说明](docs/development/versioning.md)。

v4.6.0-l 的插件开发见 [插件指南](docs/development/plugins/README.md)，按 Core、Agent 和管理扩展划分，包含已编译的工具、组件、System、事件与异步环绕示例。

版本功能与升级说明见 [发布日志](docs/changelog/README.md)，当前计划及已完成记录见 [计划索引](docs/plan/README.md)。

## ⚙️ 平台客户端部署
该项目的应用端口位于 `src/main/resources/application.properties` 中 `server.port` 配置，默认为 `6691`。

<details>
<summary>Onebot 11</summary>

---

本项目原生支持 [Onebot 11 协议](https://github.com/botuniverse/onebot-11/blob/master/api/public.md) 的任意客户端。

配置说明：
- 创建 WebSocket 客户端
- 配置服务器地址 `ws://<服务器地址>:<应用端口>/ws/onebot`

你可以使用：
- [NapCat](https://github.com/NapNeko/NapCatQQ)
- [LLOnebot](https://github.com/LLOneBot/LuckyLilliaBot)
- ~~(已归档) [Lagrange.Core](https://github.com/LagrangeDev/Lagrange.Core/tree/master)~~

---

</details>

---

## 🛠️ 开发配置说明

开发环境下，位于 `src/main/resources/` 目录下额外创建新配置文件 `application-dev.properties`，内容同 `application-prod.properties`。

并在运行虚拟机参数下添加

```shell
-Dspring.profiles.active=dev
```

开发热重启会断开 OneBot 连接，平台端应启用自动重连；客户端在停机前关闭连接并释放收发执行器。停机期间关闭握手遇到已关闭通道仅记调试日志，运行期传输故障仍记错误。

默认关闭 DevTools 的条件装配差异报告，保留自动热重启。需要比较装配变化时设置 `spring.devtools.restart.log-condition-evaluation-delta=true`。运行中的项目执行 `clean`、强制重新编译或整批覆盖 class 文件会触发多次重启，甚至在编译未完成时临时缺少业务 Bean；这类构建应先停止 IntelliJ 中的应用，构建完成后重新运行。

IntelliJ 直接运行主类时，可在 VM options 加入 `--enable-native-access=ALL-UNNAMED`，允许 Netty 的原生库访问并消除 Java 25 的原生访问提示；Gradle 的 `bootRun` 和可执行 JAR 已有对应配置。

---

## 🌐 Web 控制台配置

Web 默认关闭，提供 Agent、聊天配置、本地文件配置、任务、日志、客户端指令和网页聊天管理。完整字段和操作说明见 [Web 控制台部署与开发](docs/development/web-console.md)。以下命令均在项目根目录执行，Windows 使用 PowerShell；其他系统将 `.\gradlew.bat` 换成 `./gradlew`。

聊天配置分为 Agent 与其他功能，可按平台和聊天登记、修改、注销。普通配置需在本地安全文件中允许对应 `settings-sections`，或设置 `settings-groups: ['general']` 开放普通配置组；仅发现配置不会自动开放。任务与日志观测配置可通过 `file-sections` 中的 `core.runtime` 开放，保存后单独应用。

“任务与日志”可按分类、规则类型、状态和名称筛选实际定时任务；Spring 与关键维护任务只读，可管理的 Core Timer 保留暂停、恢复和原回执查询。详见[任务与定时规则](docs/development/web-console-runtime.md)。

概览可查看 CPU、物理内存、JVM 内存与运行时长，仅在页面可见时自动刷新。版本读取实际构建清单或生成资源，Gradle 构建和类路径启动共用原版本来源。

控制台默认使用简洁模式，右上角可切换高级模式和主题。详细说明按需展开，切换模式会保留当前草稿；桌面侧栏可折叠，手机使用抽屉导航。浅色背景为 `#F5F3ED`，本地文件配置保存后仍需单独应用。

### 统一端口配置

两个端口统一在 `src/main/resources/application.properties` 设置：

```properties
server.port=6691
tea-neko.web.frontend.port=5173
```

`server.port` 是后端 HTTP/WebSocket 端口；`tea-neko.web.frontend.port` 是本机 Vite 的 HTTPS/WSS 开发端口。它们由两个进程分别监听，不能设置成同一个端口。修改后重启对应开发进程。Vite 自动派生 `/api/`、`/ws/` 的代理目标和转发来源，无须再编辑 `vite.config.ts` 中的数字。

联合启动时，后端把 Spring 实际监听端口和前端配置传给 Vite，因此后端 Profile、环境变量或启动参数覆盖端口后，代理也会跟随。单独执行 `npm run dev` 时读取上述文件中的十进制端口值；若后端另有运行时覆盖，可通过 `TEANEKO_BACKEND_PORT`、`TEANEKO_FRONTEND_PORT` 环境变量传入实际值。

`config/web/security.yml` 的 `public-origin` 仍声明允许的完整访问来源；改变开发前端端口后须同步修改该地址，浏览器和 OneBot 连接也使用新端口。生产环境由后端提供静态页面，只使用后端端口，外部 HTTPS 地址由生产代理决定。下文地址均以默认端口为例。

### 本地启用

在 `config/web/security.yml` 修改以下字段，保留已有的配置区、文件区和管理资源白名单：

```yaml
enabled: true
public-origin: 'https://localhost:5173'
admin-token: '${TEANEKO_WEB_ADMIN_TOKEN:}'
trusted-proxies: ['127.0.0.1']
```

`public-origin` 是浏览器实际使用的 HTTPS 来源，不包含 `/console/` 或末尾斜线。上例信任本机 Vite 开发代理，后端默认端口为 `6691`。`admin-token` 可直接填写三十二至二百五十六字符、不含空白的随机令牌；上例从环境变量读取，未设置时每次启动自动生成。

如果需要网页指令台，在原文件中设置 `console-prefixes: ['/console']`。其余白名单及参数见 [完整配置示例](docs/development/web-console.md#首次启用)。

### 一条命令启动前后端

先准备好 `application-dev.properties` 中的数据库等后端配置，再执行：

```powershell
.\gradlew.bat bootRun "-PspringProfile=dev,web-dev"
```

`dev` 加载开发配置；`web-dev` 启用自动启动前端的组件，不要求存在同名配置文件。Gradle 会安装并构建前端依赖，后端就绪后自动启动 Vite。看到 `VITE ... ready` 后，访问 **https://localhost:5173/console/**，首次接受本地开发自签证书。

正常关闭或 DevTools 热重启后端时，会清理本实例启动的前端及子进程。单独使用 `dev` 只启动后端；启用 `security.yml` 或打印登录链接并不表示 Vite 已启动。若要手动启动前端，在 `frontend` 目录执行 `npm ci`、`npm run dev`，此时不添加 `web-dev`。

### IntelliJ 启动前后端

打开 **运行 → 编辑配置**，选择后端运行配置，将 **Working directory** 设为项目根目录。首次执行 `.\gradlew.bat webInstall` 准备前端依赖，然后任选一种方式设置 Profile：

| 配置位置 | 填写内容 |
| --- | --- |
| Spring Boot 配置的 **Active profiles** | `dev,web-dev` |
| **VM options（虚拟机选项）** | `-Dspring.profiles.active=dev,web-dev` |
| **Program arguments（程序参数）** | `--spring.profiles.active=dev,web-dev` |

Active profiles 表示本次启用的配置环境；未显示时可在 Modify options 中启用，或直接采用 VM options。将原来的 `dev` 设置替换为上表对应值，避免保留相互冲突的设置。`--spring.profiles.active=...` 必须放在程序参数中，放进 VM options 会导致 JVM 报 `Unrecognized option`。

### 登录与保存令牌

启用 Web 后，后端启动 INFO 日志会显示网站地址、当前登录令牌和带 `#token=...` 的快捷链接。随机令牌同时保存在 `data/web/runtime-admin-token`，重启后更新；后端日志含管理员凭据，应限制其访问权限，启动凭据不会进入网页日志窗口。

快捷链接会自动填入令牌并移除当前地址中的令牌，仍需点击登录；也兼容 `?token=...`，推荐使用不发送给服务器的 `#token=...`。勾选“在此浏览器保存令牌”并成功登录后，下次访问会自动填入；取消勾选并成功登录即可清除。随机令牌变更后需要重新登录并更新保存值。

### 生产环境启动

准备生产数据库配置，并将 `config/web/security.yml` 中的 `public-origin` 改为正式来源，例如 `https://console.example.com`。`trusted-proxies` 填实际反向代理的传输地址；代理与后端同机时通常为 `127.0.0.1`。

使用 Gradle 启动生产环境：

```powershell
.\gradlew.bat bootRun "-PspringProfile=prod"
```

也可构建后部署 JAR，下面以 `4.7.0-l` 为例，实际文件名以 `version.properties` 对应版本为准：

```powershell
.\gradlew.bat bootJar
java -jar build/libs/TeaNeko-App-4.7.0-l.jar --spring.profiles.active=prod
```

生产环境由后端直接提供构建好的前端页面，只使用 `prod`，不添加 `web-dev`；运行已打包 JAR 不需要 Node。配置 HTTPS 反向代理转发到后端 `http://127.0.0.1:6691`，覆盖设置 `X-Forwarded-Proto` 和 `X-Forwarded-Host`，并限制公网直接访问后端端口。访问地址为 **`https://你的域名/console/`**，不再使用开发端口 `5173`。

### 启用 Web 后的 OneBot 连接

启用 Web 会同时要求机器连接使用独立认证和 HTTPS/WSS。继续使用 OneBot 时，在安全配置中设置：

```yaml
machine-tokens:
  onebot: '${TEANEKO_ONEBOT_ACCESS_TOKEN}'
```

启动前设置该环境变量。在 OneBot 客户端配置反向地址 `wss://你的域名/ws/onebot` 和对应 Bearer 认证令牌，反向代理须支持 WebSocket 升级。机器令牌允许 0～256 字符；明确设置 `onebot: ''` 表示允许任意令牌或省略认证头，省略整个客户端则拒绝连接。非空值不能含空白或与管理员令牌相同。

网页“客户端 → 机器令牌”支持查看、复制、修改、移除和空白放行。先保存，再单独应用；应用后使用原管理员令牌重新登录。完整流程见[客户端与机器令牌](docs/development/web-console-clients.md)。

本地 `web-dev` 开发时使用 `wss://localhost:5173/ws/onebot`，Vite 会将连接和认证头转发到后端。OneBot 客户端须信任开发自签证书；浏览器接受证书不代表机器客户端已信任。`6691` 默认是 HTTP 端口，直接使用 `wss://localhost:6691` 会报 `WRONG_VERSION_NUMBER` 或 HTTP 方法包含无效字符，应连接提供 TLS 的代理入口。

### 无法打开网页时

- **连接被拒绝**：确认后端成功启动，实际启用 `dev,web-dev`，并看到 Vite 的就绪输出；`6691` 是后端端口，`5173` 是开发前端端口。
- **Vite 立即退出**：查看后端终端中的 Vite 错误，检查 Node 版本、工作目录、前端依赖和端口占用；自动启动不会接管已有的手动 Vite 进程。
- **页面可打开但无法登录**：检查 `enabled`、`public-origin` 是否与浏览器地址一致，以及 `trusted-proxies` 是否包含实际代理地址；使用当前启动的令牌。

---

## 🤝 交流
|   交流方式   | 说明                                      |
|:--------:|-----------------------------------------|
|  Issue   | 欢迎提交 Issue 来报告 Bug 或提出功能建议。             |
|    PR    | 欢迎 fork 项目并提交 PR 来改进项目。                 |
| QQ Group | [开发交流群](https://qm.qq.com/q/8pFJ2yOure) |

---

## 📄 许可证

本项目遵循仓库中的 `LICENSE` 协议。
