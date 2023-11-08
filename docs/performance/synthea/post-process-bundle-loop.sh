#!/bin/sh -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

DIR="$1"

while true; do
  echo "Post process $(find "$DIR" -name '*.json' | wc -l) bundles..."
  "$SCRIPT_DIR/post-process-bundles.sh" "$DIR"
  sleep 10
done
