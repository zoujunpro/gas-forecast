#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
pid_file="$root/model.pid"
if command -v launchctl >/dev/null 2>&1; then
  launchctl remove com.gas.forecast.model >/dev/null 2>&1 || true
fi
[ -f "$pid_file" ] || { echo '模型服务未运行'; exit 0; }
pid=$(cat "$pid_file")
kill "$pid" 2>/dev/null || true
rm -f "$pid_file"
echo '模型服务已停止'
