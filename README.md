<p align="center">
<img src="docs/img/cover.png" alt="TeaNeko-App Cover" width="1441" />
<br>

<!-- 项目信息 -->
<img src="https://img.shields.io/github/license/Tea-Neko-Party/TeaNeko-App" alt="License">
<img src="https://img.shields.io/github/repo-size/Tea-Neko-Party/TeaNeko-App" alt="Repo Size">
<img src="https://img.shields.io/github/v/tag/Tea-Neko-Party/TeaNeko-App?style=flat-square" alt="Version">

<!-- 技术栈 -->
<img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
<img src="https://img.shields.io/badge/Spring_Boot-4.0.3-brightgreen" alt="Spring Boot 4.0.3">
<img src="https://img.shields.io/badge/Gradle-9.3.0-02303A" alt="Gradle 9.3.0">

</p>

TeaNeko-App 是专注于多平台聊天机器人的高性能架构，允许快速扩展多平台适配器和插件，提供稳定的核心功能和丰富的开发工具。此外，TeaNeko-App 未来将会扩展 LLM-Agent 相关功能。

---

## 🚀 构建说明
| 步骤 | 说明                                      | bash                                                          |
|:--:|-----------------------------------------|---------------------------------------------------------------|
| 1  | - 安装 Java 21<br> - 安装任意兼容 `JDBC` 的数据库环境 |                                                               |
| 2  | 克隆该项目                                   | `git clone https://github.com/Tea-Neko-Party/TeaNeko-App.git` |
| 3  | 配置数据库连接信息                               | 位于 `src/main/resources/application-dev.properties`            |
| 4  | 构建项目                                    | `./gradlew build -x test`                                     |
| 5  | 复制项目样板配置                                | `./gradlew copyTemplatesToConfig`                             |
| 6  | 根据注释配置 `config` 目录下配置文件                 |                                                               |
| 7  | 启动项目                                    | `./gradlew bootRun`                                           |

---

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