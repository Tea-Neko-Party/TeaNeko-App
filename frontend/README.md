# TeaNeko 控制台前端

使用 Vue 3、TypeScript、Vite、Element Plus。当前页面为登录、运行概览、聊天平台与当前会话；数据来自受保护后端，没有独立模型循环或浏览器持久令牌。

构建与 HTTPS 开发步骤、配置、代理、机器接入及验收见[Web 控制台说明](../docs/development/web-console.md)。`npm run build` 执行类型检查并写入 build/generated/web/console；Gradle 通过 webInstall/webBuild 将产物打包到两个 JAR。

应用版本只由根目录 version.properties 管理，页面查询后端；package.json 不维护第二套应用版本。依赖版本及兼容性由锁文件固定，不直接追随不兼容的主版本升级。
