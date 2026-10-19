#!/bin/sh
set -eu
root=${1:-/Users/zoujun/data/gas}
"$root/gas-model/bin/start.sh"
"$root/gas-service/bin/start.sh"
"$root/gas-agent/bin/start.sh"
