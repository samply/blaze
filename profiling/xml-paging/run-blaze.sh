#!/usr/bin/env bash

set -euo pipefail

image="${1:-blaze:latest}"
container="${BLAZE_CONTAINER:-blaze-paging}"
volume="${BLAZE_DATA_VOLUME:-blaze-paging-data}"
port="${BLAZE_PORT:-8080}"
admin_port="${BLAZE_ADMIN_PORT:-8081}"
heap="${BLAZE_HEAP:-4g}"

if docker ps -a --format '{{.Names}}' | grep -qx "$container"; then
  docker rm -f "$container" >/dev/null
fi

docker volume create "$volume" >/dev/null

docker run \
  --name "$container" \
  -d \
  -e "JAVA_TOOL_OPTIONS=-Xmx${heap}" \
  -e ENABLE_ADMIN_API=true \
  -p "${port}:8080" \
  -p "${admin_port}:8081" \
  -v "${volume}:/app/data" \
  "$image" >/dev/null

url="http://localhost:${port}/health"
printf 'Waiting for %s' "$url"
for _ in $(seq 1 120); do
  if curl -fsS "$url" >/dev/null 2>&1; then
    printf '\n'
    echo "Blaze is ready: http://localhost:${port}/fhir"
    exit 0
  fi
  printf '.'
  sleep 1
done

printf '\n' >&2
docker logs "$container" >&2 || true
echo "Blaze didn't become healthy in time." >&2
exit 1
