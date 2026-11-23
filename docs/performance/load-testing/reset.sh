#!/bin/bash
set -euo pipefail

# Restarts the Blaze of the given system with an empty database.
#
# Shuts the container down, deletes the contents of the host directory that the
# Compose file of the system mounts as data volume, and of the host directories
# mounted below it, like separate WAL directories, starts the container again
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

# The data volume and the bind mounts below it, like the ones of WAL directories
# that live on another disk. All of them have to be emptied, because Blaze
# can't start with the WAL files of a previous run but without its database.
DATA_DIRS_LINES="$(docker compose "${COMPOSE_ARGS[@]}" config --format json |
  jq -r '.services.blaze.volumes[]
    | select(.type == "bind" and (.target == "/app/data" or (.target | startswith("/app/data/"))))
    | .source')"
mapfile -t DATA_DIRS <<<"$DATA_DIRS_LINES"

# The contents of the directories are deleted, so be strict about what they are.
for DATA_DIR in "${DATA_DIRS[@]}"; do
  if [[ "$DATA_DIR" != /?* ]]; then
    echo "the data volume of $COMPOSE_FILE and the bind mounts below it have to be absolute host directories but one was: $DATA_DIR" >&2
    exit 1
  fi

  if [ ! -d "$DATA_DIR" ]; then
    echo "the data directory $DATA_DIR doesn't exist" >&2
    exit 1
  fi
done

docker compose "${COMPOSE_ARGS[@]}" down

for DATA_DIR in "${DATA_DIRS[@]}"; do
  echo "deleting the contents of $DATA_DIR"
  find "$DATA_DIR" -mindepth 1 -delete
done

docker compose "${COMPOSE_ARGS[@]}" up -d --wait
