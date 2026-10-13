#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
pid_file="$root/service.pid"
[ -f "$pid_file" ] || { echo 'Java 服务未运行'; exit 0; }
pid=$(cat "$pid_file")

# 本机故障恢复时服务可能由 launchd 临时托管；正式部署前先解除托管，避免被自动拉起并占用端口。
if command -v launchctl >/dev/null 2>&1; then
  launchctl remove com.gas.forecast.service >/dev/null 2>&1 || true
fi
kill "$pid" 2>/dev/null || true

# 等待 Spring Boot 完成优雅退出，避免紧接着启动新版本时端口仍被占用。
waited=0
while kill -0 "$pid" 2>/dev/null && [ "$waited" -lt 30 ]; do
  sleep 1
  waited=$((waited + 1))
done
if kill -0 "$pid" 2>/dev/null; then
  echo "Java 服务在 30 秒内未退出，强制终止 PID=$pid"
  kill -9 "$pid" 2>/dev/null || true
fi
rm -f "$pid_file"
echo 'Java 服务已停止'
