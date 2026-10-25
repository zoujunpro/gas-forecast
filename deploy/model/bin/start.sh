#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
pid_file="$root/model.pid"
service_label="com.gas.forecast.model"
plist="$HOME/Library/LaunchAgents/$service_label.plist"
if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
  echo "模型服务已运行，PID=$(cat "$pid_file")"
  exit 0
fi
if [ -f "$root/config/model.env" ]; then set -a; . "$root/config/model.env"; set +a; fi
export GAS_MODEL_CONFIG="$root/current/config/application.yaml"
export GAS_MODEL_PROFILE="${GAS_MODEL_PROFILE:-prod}"
export GAS_MODEL_ARTIFACT_ROOT="${GAS_MODEL_ARTIFACT_ROOT:-$root/artifacts}"
export GAS_MODEL_LOG_DIR="${GAS_MODEL_LOG_DIR:-$root/logs}"

if [ -f "$plist" ]; then
  if ! launchctl print "gui/$(id -u)/$service_label" >/dev/null 2>&1; then
    launchctl bootstrap "gui/$(id -u)" "$plist"
  fi
  launchctl kickstart -k "gui/$(id -u)/$service_label"
  echo "模型服务已交由 launchd 启动"
  exit 0
fi

cd "$root"
nohup "$root/venv/bin/python" -m gas_model_platform.main >> "$root/logs/console.log" 2>&1 &
echo "$!" > "$pid_file"
echo "模型服务已启动，PID=$!"
