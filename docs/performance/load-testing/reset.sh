#!/bin/bash
set -euo pipefail

# Restarts the Blaze of the given system with an empty database.
#
# Shuts the container down, deletes the contents of the host directory that the
# Compose file of the system mounts as data volume, starts the container again
# and waits for Blaze to become healthy.
#
# The optional second argument is a Compose file with additions to the one of
# the system, like docker-compose-profiling.yml, that Blaze is started with.
#
# Usage: ./reset.sh <system> [<additional compose file>]

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
SYSTEM="$1"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-$SYSTEM.yml"
COMPOSE_ARGS=(-f "$COMPOSE_FILE")

if [ -n "${2:-}" ]; then
  COMPOSE_ARGS+=(-f "$2")
fi

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "missing Compose file $COMPOSE_FILE" >&2
  exit 1
fi

DATA_DIR="$(docker compose -f "$COMPOSE_FILE" config --format json |
  jq -r '.services.blaze.volumes[] | select(.target == "/app/data" and .type == "bind") | .source')"

# The contents of the directory are deleted, so be strict about what it is.
if [[ "$DATA_DIR" != /?* ]]; then
  echo "the data volume of $COMPOSE_FILE has to be an absolute host directory but was: $DATA_DIR" >&2
  exit 1
fi

if [ ! -d "$DATA_DIR" ]; then
  echo "the data directory $DATA_DIR doesn't exist" >&2
  exit 1
fi

docker compose "${COMPOSE_ARGS[@]}" down

echo "deleting the contents of $DATA_DIR"
find "$DATA_DIR" -mindepth 1 -delete

docker compose "${COMPOSE_ARGS[@]}" up -d --wait
