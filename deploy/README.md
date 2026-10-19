# Jenkins 本机部署

流水线构建并部署以下产物：

- `gas-service`：Spring Boot 可执行 JAR，部署端口默认 `18081`。
- `gas-model`：Python wheel + 独立虚拟环境，部署端口默认 `18090`。
- `gas-agent`：Python wheel + 独立虚拟环境，部署端口默认 `18091`。
- `gas-web`：Vue 构建后的静态文件，发布到 `current` 软链接；由 Nginx 或其他静态服务器托管。

敏感配置不进入 Git。第一次发布前，将示例文件复制为真实配置：

```bash
cp /Users/zoujun/data/gas/gas-service/config/service.env.example /Users/zoujun/data/gas/gas-service/config/service.env
cp /Users/zoujun/data/gas/gas-model/config/model.env.example /Users/zoujun/data/gas/gas-model/config/model.env
cp /Users/zoujun/data/gas/gas-agent/config/agent.env.example /Users/zoujun/data/gas/gas-agent/config/agent.env
```

四个 Jenkins 任务使用页面配置的构建步骤。构建、创建 release、复制产物和切换
`current` 等发布动作直接写在 Pipeline 中。Java 和模型服务目录中的脚本只负责启动或停止；
前端是静态文件，由 Nginx 托管，不单独启动进程。

本机公共 Nginx 使用 `deploy/nginx/gas-web-dev.conf`，Web 入口为
`http://localhost:18082`。普通业务接口转发至 Java 服务 `18081`；
`/agent-api/` 对话入口转发至 Python Agent `18091`，用户 Token 由 Agent 原样透传给 Java MCP 校验。

手动管理服务：

```bash
/Users/zoujun/data/gas/bin/start-all.sh /Users/zoujun/data/gas
/Users/zoujun/data/gas/bin/stop-all.sh /Users/zoujun/data/gas
```

也可以分别管理服务：

```bash
/Users/zoujun/data/gas/gas-service/bin/start.sh
/Users/zoujun/data/gas/gas-service/bin/stop.sh
/Users/zoujun/data/gas/gas-model/bin/start.sh
/Users/zoujun/data/gas/gas-model/bin/stop.sh
/Users/zoujun/data/gas/gas-agent/bin/start.sh
/Users/zoujun/data/gas/gas-agent/bin/stop.sh
```
