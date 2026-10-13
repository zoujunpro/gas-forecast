#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
pid_file="$root/model.pid"
if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
  echo "模型服务已运行，PID=$(cat "$pid_file")"
  exit 0
fi
if [ -f "$root/config/model.env" ]; then set -a; . "$root/config/model.env"; set +a; fi
export GAS_MODEL_CONFIG="$root/current/config/application.yaml"
export GAS_MODEL_PROFILE="${GAS_MODEL_PROFILE:-prod}"
export GAS_MODEL_ARTIFACT_ROOT="${GAS_MODEL_ARTIFACT_ROOT:-$root/artifacts}"
export GAS_MODEL_LOG_DIR="${GAS_MODEL_LOG_DIR:-$root/logs}"
cd "$root"
nohup "$root/venv/bin/python" -m gas_model_platform.main >> "$root/logs/console.log" 2>&1 &
echo "$!" > "$pid_file"
echo "模型服务已启动，PID=$!"
