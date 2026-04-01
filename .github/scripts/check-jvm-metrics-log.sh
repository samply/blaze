#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"
service="$2"

log_line="$(docker compose -f "$compose_file" logs "$service" | grep "Heap:" | head -1)"

test_non_empty "JVM metrics log line" "$log_line"
