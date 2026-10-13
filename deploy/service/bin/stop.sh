#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
pid_file="$root/service.pid"
[ -f "$pid_file" ] || { echo 'Java 服务未运行'; exit 0; }
pid=$(cat "$pid_file")
kill "$pid" 2>/dev/null || true
rm -f "$pid_file"
echo 'Java 服务已停止'
