#!/bin/bash
set -euo pipefail

# Starts sampling system-wide CPU, memory and IO statistics every 2 seconds in
# the background under the name $1. Stop it with stop-vmstat.sh using the same
# name.

name="$1"

nohup vmstat -n -t 2 > "$name.vmstat.log" 2>&1 &
echo $! > "$name.vmstat.pid"
