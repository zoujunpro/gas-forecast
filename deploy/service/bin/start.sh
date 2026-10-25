#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
pid_file="$root/service.pid"
service_label="com.gas.forecast.service"
plist="$HOME/Library/LaunchAgents/$service_label.plist"
if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
  echo "Java 服务已运行，PID=$(cat "$pid_file")"
  exit 0
fi
if [ -f "$root/config/service.env" ]; then set -a; . "$root/config/service.env"; set +a; fi

if [ -f "$plist" ]; then
  if ! launchctl print "gui/$(id -u)/$service_label" >/dev/null 2>&1; then
    launchctl bootstrap "gui/$(id -u)" "$plist"
  fi
  launchctl kickstart -k "gui/$(id -u)/$service_label"
  echo "Java 服务已交由 launchd 启动"
  exit 0
fi

cd "$root"
nohup java ${JAVA_OPTS:--Xms512m -Xmx2g} -jar "$root/current/gas-forecast.jar" \
  --spring.profiles.active=dev >> "$root/logs/console.log" 2>&1 &
echo "$!" > "$pid_file"
echo "Java 服务已启动，PID=$!"
