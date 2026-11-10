<p align="center">
<img src="docs/img/cover.png" alt="TeaNeko App" width="1441" />
<br>
<img src="https://img.shields.io/github/license/Tea-Neko-Party/TeaNeko-App" alt="许可证">
<img src="https://img.shields.io/github/repo-size/Tea-Neko-Party/TeaNeko-App" alt="Repo Size">
<img src="https://img.shields.io/github/v/tag/Tea-Neko-Party/TeaNeko-App?style=flat-square" alt="版本">
<img src="https://img.shields.io/badge/Java-25-orange" alt="Java 25">
</p>

# TeaNeko App

**可装配 Agent 的多平台聊天机器人。**

通过平台客户端或网页聊天与 Agent 对话，在 Web 控制台管理模型、聊天配置、记忆和运行状态。

[构建与启动](#构建与启动) · [平台接入](#平台客户端部署) · [开发环境](#开发环境) · [详细文档](#详细文档) · [问题反馈](https://github.com/Tea-Neko-Party/TeaNeko-App/issues)

## 核心能力

| 场景 | 能力 |
| --- | --- |
| Agent 对话 | 图文输入、模型路由及工具调用 |
| 组件装配 | 按聊天选择上下文、记忆、人格、表达等组件 |
| Web 管理 | 模型与聊天配置、插件总管理、插件独立页面、任务、日志及机器状态 |
| 平台接入 | 反向 WebSocket、独立网页聊天 |
| 插件开发 | 依赖声明、可重复热启停、注解工具、组件与系统、独立配置页面 |

## 构建与启动

需要 **JDK 25、Node.js 22.12 或以上版本、npm 和 MySQL**。使用仓库自带的 Gradle Wrapper，无需另装 Gradle；Windows PowerShell 将下文的 `./gradlew` 替换为 `.\gradlew.bat`。

| 步骤 | 命令或配置位置 | 说明 |
| :---: | --- | --- |
| 1 · 获取源码 | `git clone https://github.com/Tea-Neko-Party/TeaNeko-App.git`<br>`cd TeaNeko-App` | 后续命令均在项目根目录执行 |
| 2 · 执行权限 | `chmod +x gradlew` | 仅 Linux / macOS 首次使用时执行 |
| 3 · 生成配置 | `./gradlew copyTemplatesToConfig` | 补充缺少的模板，不覆盖已有配置 |
| 4 · 配置连接 | `src/main/resources/application-prod.properties`<br>`config/web/security.yml` | 分别配置数据库、控制台 HTTPS 来源与可信代理 |
| 5 · 构建项目 | `./gradlew bootJar` | 自动安装前端依赖、检查并打包网页 |
| 6 · 准备数据库 | [安装与迁移](docs/development/database-upgrades.md) | 备份后执行配套迁移；生产启动只校验结构 |
| 7 · 启动服务 | `./gradlew bootRun` | 默认 **`prod`**，同一后端提供网页和 API |

构建产物为 `build/libs/TeaNeko-App-版本号.jar`；需要构建并运行测试时使用 `./gradlew build`。生产通过 HTTPS 代理访问 `https://你的域名/console/`。环境变量、JAR 部署及更多命令见下方“详细文档”中的构建指南。

## Web 控制台

控制台模板默认启用；已有配置不自动更改。生产使用后端提供的网页，本地联合开发使用 Vite。

启动后从后端 INFO 日志获取登录地址和管理员令牌。未指定令牌时每次启动随机生成，也可在 `config/web/security.yml` 配置。完整登录、HTTPS、模型与文件配置说明见下方文档导航。

v4.9.0-l 将认知、表达、社交、自动化及平台业务归入分类插件。插件总管理统一保存开关至 `config/plugins.yml`；关闭插件会隐藏其业务入口并保留数据与原组件选择。插件页面随前端构建交付。登录后默认开放管理资源；显式开启本地白名单收紧时，仍保留原精确授权范围，详见[插件管理与升级](docs/development/plugins/management.md)。

v4.9.1-l 增加右侧插件配置二级菜单、插件名称筛选及按领域导航的功能弹窗。模型页保存后自动应用，新路由进入 LLM 选择；继承默认的聊天使用最新默认模型，显式选择保持不变。完整变化见[本版发布说明](docs/changelog/4.9.1-l.md)。

模型配置同时支持 Responses、Chat Completions 和 Gemini 原生代理；新增 `antigravity` 协议提供图片、工具续轮及结构化输出，按路由独立选择。连接与分组专用路径见[模型配置](docs/development/web-console-models.md#gemini-原生代理)。

## 平台客户端部署

各平台通过客户端适配器接入，通用端口集中在 `src/main/resources/application.properties`：

| 用途 | 默认端口 | 协议 |
| --- | --- | --- |
| 后端服务与客户端直连 | `6691` | HTTP / WS |
| 本地开发前端 | `5173` | HTTPS / WSS，仅 `web-dev` 使用 |
| 生产公开入口 | 由 TLS 代理配置，通常为 `443` | HTTPS / WSS，可自定义端口 |

生产只对外开放 TLS 入口；同机或 Docker 内网客户端可直接连接后端 WS，6691 的可达范围由防火墙和容器网络控制。

<details>
<summary>OneBot 11 快速接入</summary>

支持 [OneBot 11 协议](https://github.com/botuniverse/onebot-11/blob/master/api/public.md) 的反向 WebSocket 客户端。

1. 在 `config/web/security.yml` 的 `machine-tokens.onebot` 设置机器令牌。
2. 在客户端新建反向 WebSocket 连接，填写同一令牌。
3. 同机使用 `ws://127.0.0.1:6691/ws/onebot`；Docker 同网络使用 `ws://后端服务名:6691/ws/onebot`；公网使用 `wss://你的域名/ws/onebot`，自定义端口写在域名后。

6691 默认不提供 TLS，不能直接改写为 `wss://localhost:6691`。非空机器令牌通过 Bearer 认证传递；更多令牌策略、Docker、证书与独立 WSS 端口配置见[客户端部署说明](docs/development/web-console-clients.md)。

</details>

## 开发环境

复制 `application-prod.properties` 为同目录的 `application-dev.properties`，配置测试数据库，再显式启动联合开发：

```shell
./gradlew bootRun -PspringProfile=dev,web-dev
```

访问 [本机开发控制台](https://localhost:5173/console/)，首次接受开发证书。IntelliJ 的工作目录设为项目根目录，Active profiles 填 `dev,web-dev`。开发代理和登录设置、插件编写方法见下方文档。

## 详细文档

| 分类 | 文档 | 内容 |
| --- | --- | --- |
| 构建与运行 | [构建指南](docs/development/build-and-run.md) | 数据库、Gradle 任务、JAR 部署与开发环境 |
| 数据库与容量 | [安装、升级与恢复](docs/development/database-upgrades.md) · [运行边界](docs/development/runtime-limits.md) | 生产迁移、账号权限、备份和在途限制 |
| Web 部署 | [控制台配置](docs/development/web-console.md) | HTTPS、登录令牌、代理、端口与排错 |
| 平台接入 | [客户端与机器令牌](docs/development/web-console-clients.md) | OneBot、Docker、WS / WSS 与认证 |
| 模型与配置 | [模型配置](docs/development/web-console-models.md) · [文件配置](docs/development/web-console-files.md) | 添加模型、API 密钥、环境变量与应用修改 |
| Agent | [部署说明](docs/development/agent-deployment.md) · [组件装配](docs/development/agent-composition.md) | 启用对话、装配记忆、人格与工具 |
| 网页操作 | [聊天](docs/development/web-console-chat.md) · [工作区](docs/development/web-console-agent.md) · [任务与日志](docs/development/web-console-runtime.md) | 聊天、活动、记忆与运行状态 |
| 插件开发 | [插件指南](docs/development/plugins/README.md) · [项目分层](docs/development/project-layers.md) | Core、Agent、工具、组件、事件与管理扩展 |
| 代码质量 | [格式与增量检查](docs/development/code-quality.md) · [审查索引](docs/review/README.md) · [当前剩余验收](docs/review/4.8.7-l/next-steps.md) | 共享代码样式、检查命令、已完成范围与验证边界 |
| 版本与计划 | [版本操作](docs/development/versioning.md) · [发布日志](docs/changelog/README.md) · [计划索引](docs/plan/README.md) | 构建版本、发布流程与开发记录 |

## 交流

- [Issues](https://github.com/Tea-Neko-Party/TeaNeko-App/issues)：报告问题或提出建议。
- [Pull requests](https://github.com/Tea-Neko-Party/TeaNeko-App/pulls)：提交改进。

## 许可证

本项目采用 [GNU GPLv3](LICENSE)，允许使用、修改和再分发。分发本项目及其衍生版本时，须遵守相应的许可证和源码提供要求；简要解释见 [GNU 许可说明](https://www.gnu.org/licenses/gpl-faq.html)，完整条款以仓库 LICENSE 为准。
