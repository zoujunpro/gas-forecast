#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
pid_file="$root/agent.pid"
if command -v launchctl >/dev/null 2>&1; then
  domain="gui/$(id -u)"
  service="$domain/com.gas.forecast.agent"
  launchctl bootout "$service" >/dev/null 2>&1 || true
  # bootout 为异步操作；等待服务真正移除，避免紧接着 bootstrap 返回 37。
  waited=0
  while launchctl print "$service" >/dev/null 2>&1 && [ "$waited" -lt 15 ]; do
    sleep 1
    waited=$((waited + 1))
  done
fi
[ -f "$pid_file" ] || { echo 'Agent 服务未运行'; exit 0; }
pid=$(cat "$pid_file")
kill "$pid" 2>/dev/null || true
waited=0
while kill -0 "$pid" 2>/dev/null && [ "$waited" -lt 15 ]; do
  sleep 1
  waited=$((waited + 1))
done
if kill -0 "$pid" 2>/dev/null; then
  echo "Agent 服务在 15 秒内未退出，强制终止 PID=$pid"
  kill -9 "$pid" 2>/dev/null || true
fi
rm -f "$pid_file"
echo 'Agent 服务已停止'
