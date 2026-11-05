# 客户端与机器令牌

控制台的“客户端”显示已实现管理适配器的平台。“机器令牌”只列出实际注册了 WebSocket 的平台入口，例如 OneBot；独立网页聊天不使用机器接入令牌。

## 策略

| 本地配置 | 连接行为 |
| --- | --- |
| 未声明客户端 | 拒绝连接 |
| `onebot: ''` 或纯空白 | 任意令牌或缺省 Authorization 均可连接 |
| 非空令牌 | 必须提供相同的 `Authorization: Bearer 值` |

非空令牌允许短值，最多 256 字符，不能含空白或与管理员令牌相同。管理员登录令牌继续使用原有长度和认证规则。机器策略同时用于直接 WS 和经过 TLS 代理的 WSS；网页管理继续要求 HTTPS。

## WS 与 WSS 部署

同机客户端可直接连接 `ws://127.0.0.1:6691/ws/onebot`，端口取 `server.port`，非空机器令牌仍通过 `Authorization: Bearer 值` 发送。两个认证阶段及后续消息收发继续校验令牌；该入口不把明文 WS 标记成已加密请求，也不接受管理员会话代替机器令牌。

Docker 同一网络中使用后端服务名，例如 `ws://teaneko:6691/ws/onebot`，将 `teaneko` 替换为实际服务名。后端须在容器内监听 `0.0.0.0:6691`，不要将容器内监听限制到回环地址。若仅同网络容器需要访问，可不发布后端端口；宿主机 TLS 代理需要访问时，可将端口映射绑定为 `127.0.0.1:6691:6691`。

机器入口不限制到回环 IP，因为 Docker 网桥来源通常是容器网络地址；能到达后端端口的来源均按同一机器令牌策略鉴权。后端端口保持在受控内网，公网通过 HTTPS/WSS 代理接入；网页 API 的 HTTPS、来源、会话和防伪校验保持有效。

## 自定义 WSS 端口

生产公开 WSS 端口由 TLS 代理监听，可独立于网页的 443、后端的 6691 及开发前端端口。例如设置独立公开端口 7443：

```properties
server.port=6691
tea-neko.websocket.port=0
tea-neko.websocket.public-origin=wss://bot.example.com:7443
```

为 TLS 代理配置实际监听与 WebSocket 升级转发，例如在现有 Nginx 的 `http` 配置中添加：

```nginx
server {
    listen 7443 ssl;
    server_name bot.example.com;
    ssl_certificate /etc/teaneko/tls/fullchain.pem;
    ssl_certificate_key /etc/teaneko/tls/privkey.pem;

    location /ws/ {
        proxy_pass http://127.0.0.1:6691;
        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_read_timeout 3600s;
    }

    location / {
        return 404;
    }
}
```

替换域名和证书路径，开放 TLS 入口端口并重载代理，重启后端后页面显示 `wss://bot.example.com:7443/ws/onebot`。`public-origin` 只声明公开地址，不创建 TLS 监听器；`tea-neko.websocket.port` 是可选的 HTTP/WS 代理监听端口，也不提供 TLS。

开发环境可将 `tea-neko.web.frontend.port` 改为 `5443` 等可用端口，并同步 `config/web/security.yml` 的 `public-origin` 为 `https://localhost:5443`，重启开发进程即可使用 `wss://localhost:5443/ws/onebot`。独立 WSS 公开来源留空时会跟随该 HTTPS 来源；显式设置时以配置值为准。

## 网页操作

1. 默认目录只显示策略，不返回秘密。点击“查看令牌”才获取指定客户端的实际磁盘值；环境模板会展开。离开客户端页面后自动隐藏已显示的值。
2. 点击“修改”，选择设置新令牌、空白放行或移除配置。只修改该客户端，其他秘密、未修改的环境模板及未知字段保留。
3. 保存前确认 YAML 重排及原注释丢失。保存仅替换磁盘文件，原连接继续按运行策略工作。
4. 点击“应用机器令牌”单独发布。成功后重新登录，原管理员令牌不变，包括本次启动生成的随机值；策略变更仍会撤销旧管理员会话代次。
5. 机器旧连接在下次收发时复核，新连接使用新策略。已经受理的外部动作不保证撤销。

安全文件若还有管理员令牌、外部来源或白名单等其他未应用改动，此入口会拒绝应用。先在本机确认并按原流程重载，避免借令牌操作顺带发布其他认证根设置。

## 中断与并发

每次保存、应用使用独立操作编号。网络中断时保留原请求，先查询原操作；重试使用相同编号及相同参数，不能替换秘密后复用旧编号。

浏览器仅在当前标签页的 sessionStorage 保留最近操作 UUID，方便重新登录后查询，不保存秘密或原请求。浏览器禁用存储时，关闭或刷新会丢失该编号，应先保留页面显示的 UUID。原候选秘密只留在当前组件内存。

回执复用 `data/web/file-config-operations` 的私有持久账本，只存带密钥请求摘要、版本及结果，保留期限与普通文件配置相同。状态和恢复边界见[文件配置回执](web-console-files.md#回执与故障恢复)。应用响应中断不表示没有应用；重新登录后查询原编号并核对目录运行版本。

## 接口

以下接口均要求管理员会话并禁止缓存；POST 同时校验 CSRF。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/v1/client-tokens` | 无秘密目录及磁盘、运行版本 |
| `POST /api/v1/client-tokens/{client}/reveal` | 显式查看单个客户端的磁盘令牌 |
| `POST /api/v1/client-tokens/{client}/actions/save` | 固定版本的局部保存 |
| `POST /api/v1/client-tokens/actions/apply` | 单独应用机器策略 |
| `GET /api/v1/client-tokens/operations/{operation}` | 原持久回执 |

保存请求包含 `operationId`、`expectedRevision` 和 `action`。`action` 为 `SET` 时提供原始字符串 `value`，支持环境占位符；`OPEN`、`DENY` 不提供 `value`。应用请求只包含独立的 `operationId` 与要应用的 `expectedRevision`。

连接 `wss://localhost:6691` 到默认 HTTP/WS 端口时出现的 TLS 版本错误不属于令牌校验失败。应使用该端口的 `ws://` 地址，或连接真正提供 TLS 的 WSS 端口，详见[部署说明](web-console.md#前端开发)。
