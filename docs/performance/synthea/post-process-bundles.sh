#!/bin/sh -e

script_dir="$(dirname "$(readlink -f "$0")")"

find "$1" -name '*.json' | xargs -P0 -n1 "$script_dir/post-process-bundle.sh"
