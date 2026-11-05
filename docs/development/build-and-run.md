# 构建与运行

## 环境

| 用途 | 依赖 |
| --- | --- |
| 从源码构建 | JDK 25、Node.js 22.12 或以上版本、npm |
| 本地联合开发 | 构建环境及 MySQL |
| 运行已打包 JAR（prod） | Java 25、MySQL；不需要 Node.js |

使用仓库自带的 Gradle Wrapper，无需另装 Gradle。以下命令在项目根目录执行；Windows PowerShell 将 `./gradlew` 替换为 `.\gradlew.bat`。

Linux / macOS 首次使用先执行 `chmod +x gradlew`，再调用 Wrapper。

## 首次准备

1. 默认使用 `prod`，在 `src/main/resources/application-prod.properties` 配置数据库；也可通过下文环境变量提供。测试时复制为同目录的 `application-dev.properties` 并使用独立数据库，该开发文件已被 Git 忽略。
2. 为当前数据库账号准备可用的数据库；首次建表使用 `spring.jpa.hibernate.ddl-auto=update`。
3. 执行 `./gradlew copyTemplatesToConfig`，生成缺少的本地配置，不覆盖已有文件。

数据库配置可使用环境变量：

```properties
spring.datasource.url=${TEANEKO_DB_URL}
spring.datasource.username=${TEANEKO_DB_USER}
spring.datasource.password=${TEANEKO_DB_PASSWORD}
```

例如 URL 为 `jdbc:mysql://127.0.0.1:3306/teaneko`；在启动进程的环境中设置上述变量。模型配置另见[模型教程](web-console-models.md)。

## 构建任务

| 命令 | 结果 |
| --- | --- |
| `./gradlew bootJar` | 生成可执行 JAR，不运行测试 |
| `./gradlew build` | 编译、构建前端、打包并运行测试 |
| `./gradlew build -x test` | 完整构建，跳过测试 |
| `./gradlew webBuild` | 检查 TypeScript 并构建前端 |
| `./gradlew webInstall` | 按锁文件安装前端依赖 |

前端安装和生产构建已接入 `processResources`，通常无需手动执行 npm。产物为 `build/libs/TeaNeko-App-版本号.jar`，版本来自 `version.properties`，页面也使用该构建版本。

完整测试中包含依赖应用配置的用例；普通开发优先运行所改功能的定向测试，真实数据库和模型测试单独安排。运行中的 DevTools 会响应 class 变化；执行 `clean` 或强制重编译前先停止应用。

## 默认生产启动

```shell
./gradlew bootRun
```

未显式选择环境时默认启用 `prod`：加载生产数据库配置，由同一后端提供网页静态资源和 API，不启动 Vite。控制台模板默认启用；配置正式 HTTPS 代理后访问 `https://你的域名/console/`，登录地址和令牌通过后端 INFO 日志提示。

| 方式 | 选择环境 |
| --- | --- |
| Gradle 生产启动 | `./gradlew bootRun`，也可显式加 `-PspringProfile=prod` |
| Gradle 联合开发 | `./gradlew bootRun -PspringProfile=dev,web-dev` |
| Gradle 单独调试后端 | `./gradlew bootRun -PspringProfile=dev` |
| IntelliJ 联合开发 | 导入 Gradle 项目，工作目录设为项目根目录；Active profiles 填 `dev,web-dev` |
| 显式覆盖 | 使用 `SPRING_PROFILES_ACTIVE`，或程序参数 `--spring.profiles.active=...` |

Gradle 的 `-PspringProfile` 仅在提供时设置 JVM 的活动环境；未提供时不覆盖 Spring 环境变量。活动环境完全未设置时，Gradle、直接运行主类和 JAR 均使用 `prod`。直接运行主类前先执行 `./gradlew classes`；需要消除 Netty 原生访问提示时，在 VM options 添加 `--enable-native-access=ALL-UNNAMED`。

联合开发访问 [本机控制台](https://localhost:5173/console/)，首次接受本地开发证书。手动启动前端时，后端仅使用 `dev`，另执行 `npm --prefix frontend run dev`。关闭或热重启后端会清理本实例自动启动的前端，已手动启动的服务不由后端接管。

## JAR 部署

生产使用 `prod`，不启用 `web-dev`。准备生产数据库设置，并按[控制台部署说明](web-console.md#首次启用)设置正式 HTTPS 来源及可信代理。

```shell
./gradlew bootJar
java -jar build/libs/TeaNeko-App-4.8.2-l.jar --spring.profiles.active=prod
```

JAR 文件名以本次生成结果为准。也可使用 `./gradlew bootRun -PspringProfile=prod` 从源码运行；此方式仍需构建环境。生产数据库参数可通过 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD` 提供。

JAR 已包含网页静态资源，由 HTTPS 代理转发到后端，不运行 Vite。外部访问 `https://你的域名/console/`；同机或 Docker 的 OneBot 可用内部 WS，公开 WSS 端口由 TLS 代理监听，详见[客户端部署](web-console-clients.md#ws-与-wss-部署)。

## 端口与已有配置

端口集中在 `src/main/resources/application.properties`：

| 配置 | 默认值 | 含义 |
| --- | --- | --- |
| `server.port` | `6691` | 后端 HTTP/WS |
| `tea-neko.web.frontend.port` | `5173` | 本机 Vite HTTPS/WSS；仅开发环境使用 |
| `tea-neko.websocket.port` | `0` | 共用后端端口；非零增加仅绑定本机的 HTTP/WS 监听 |
| `tea-neko.websocket.public-origin` | 空 | 公开 WSS 来源；空值跟随控制台 HTTPS 来源 |

修改前端端口后，同步修改 `config/web/security.yml` 的 `public-origin`。Docker 网络、独立 WSS 端口及证书处理见[客户端与机器令牌](web-console-clients.md)。

模板默认启用控制台，允许本机开发代理；已有 `config/web/security.yml` 不自动覆盖。若旧文件关闭了控制台，设置 `enabled: true`；本地开发使用 `public-origin: 'https://localhost:5173'` 和 `trusted-proxies: ['127.0.0.1']`。显式选择的 Profile 也保持优先，不会强制加入开发前端。
