#!/bin/sh -e

script_dir="$(dirname "$(readlink -f "$0")")"

dir="$1"

while true; do
  echo "Post process $(find "$dir" -name '*.json' | wc -l) bundles..."
  "$script_dir/post-process-bundles.sh" "$dir"
  sleep 10
done
