#!/usr/bin/env bash

set -euo pipefail

jar="${1:-target/blaze-1.7.0-standalone.jar}"
runtime_dir="${BLAZE_RUNTIME_DIR:-profiling/xml-paging/runtime}"
data_dir="${BLAZE_DATA_DIR:-${runtime_dir}/data}"
port="${BLAZE_PORT:-8080}"
admin_port="${BLAZE_ADMIN_PORT:-8081}"
heap="${BLAZE_HEAP:-4g}"
pid_file="${runtime_dir}/blaze.pid"
log_file="${runtime_dir}/blaze.log"

if [[ ! -f "$jar" ]]; then
  echo "Jar not found: $jar" >&2
  echo "Build it first with: make uberjar" >&2
  exit 1
fi

mkdir -p "$runtime_dir" "$data_dir"

if [[ -f "$pid_file" ]]; then
  old_pid="$(cat "$pid_file")"
  if kill -0 "$old_pid" 2>/dev/null; then
    kill "$old_pid"
    while kill -0 "$old_pid" 2>/dev/null; do
      sleep 1
    done
  fi
  rm -f "$pid_file"
fi

export STORAGE=standalone
export BASE_URL="http://localhost:${port}/fhir"
export SERVER_PORT="$port"
export ADMIN_SERVER_PORT="$admin_port"
export ENABLE_ADMIN_API=true
export INDEX_DB_DIR="${data_dir}/index"
export TRANSACTION_DB_DIR="${data_dir}/transaction"
export RESOURCE_DB_DIR="${data_dir}/resource"
export ADMIN_INDEX_DB_DIR="${data_dir}/admin-index"
export ADMIN_TRANSACTION_DB_DIR="${data_dir}/admin-transaction"
export JAVA_TOOL_OPTIONS="-Xmx${heap} ${JAVA_TOOL_OPTIONS:-}"

nohup java -jar "$jar" >"$log_file" 2>&1 &
echo "$!" > "$pid_file"

url="http://localhost:${port}/health"
printf 'Waiting for %s' "$url"
for _ in $(seq 1 120); do
  if curl -fsS "$url" >/dev/null 2>&1; then
    printf '\n'
    echo "Blaze is ready: http://localhost:${port}/fhir"
    echo "Log: $log_file"
    exit 0
  fi
  printf '.'
  sleep 1
done

printf '\n' >&2
tail -100 "$log_file" >&2 || true
echo "Blaze didn't become healthy in time." >&2
exit 1
