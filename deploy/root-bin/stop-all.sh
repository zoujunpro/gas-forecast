#!/bin/sh
set -eu
root=${1:-/Users/zoujun/data/gas}
"$root/gas-agent/bin/stop.sh"
"$root/gas-service/bin/stop.sh"
"$root/gas-model/bin/stop.sh"
