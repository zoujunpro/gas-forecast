#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
pid_file="$root/agent.pid"
if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
  echo "Agent 服务已运行，PID=$(cat "$pid_file")"
  exit 0
fi
if [ -f "$root/config/agent.env" ]; then set -a; . "$root/config/agent.env"; set +a; fi
: "${GAS_MCP_URL:?缺少 GAS_MCP_URL，无法连接 Java MCP 服务}"
: "${GAS_ENTITY_RESOLVE_URL:?缺少 GAS_ENTITY_RESOLVE_URL，无法进行实体校验}"
cd "$root"
nohup "$root/venv/bin/gas-forecast-agent" >> "$root/logs/console.log" 2>&1 &
echo "$!" > "$pid_file"
echo "Agent 服务已启动，PID=$!"
