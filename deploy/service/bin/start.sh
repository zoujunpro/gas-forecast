#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
pid_file="$root/service.pid"
if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
  echo "Java 服务已运行，PID=$(cat "$pid_file")"
  exit 0
fi
if [ -f "$root/config/service.env" ]; then set -a; . "$root/config/service.env"; set +a; fi
cd "$root"
nohup java ${JAVA_OPTS:--Xms512m -Xmx2g} -jar "$root/current/gas-forecast.jar" \
  --spring.profiles.active=dev >> "$root/logs/console.log" 2>&1 &
echo "$!" > "$pid_file"
echo "Java 服务已启动，PID=$!"
