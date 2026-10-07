#!/bin/bash
set -euo pipefail

# Prints the per-level median of the given result files of the transaction test.
#
# The median is taken per column, so every value is one that was measured, but a
# line can mix values from different runs. That needs an odd number of runs.

HEADER="$(head -n 1 "$1")"
LEVELS="$(tail -n +2 "$1" | cut -d, -f1)"

if (( $# % 2 == 0 )); then
  echo "an odd number of runs is required but got $#" >&2
  exit 1
fi

# prints the values of the column $2 at the level $1 of all runs
values() {
  local LEVEL="$1" COLUMN="$2" FILE VALUE
  shift 2
  for FILE in "$@"; do
    VALUE="$(grep "^$LEVEL," "$FILE" | cut -d, -f"$COLUMN")"
    if [ -z "$VALUE" ]; then
      echo "missing level $LEVEL in $FILE" >&2
      exit 1
    fi
    echo "$VALUE"
  done
}

median() {
  values "$@" | sort -g | sed -n "$(( ($# - 2 + 1) / 2 ))p"
}

echo "$HEADER"

for LEVEL in $LEVELS; do
  VERSIONS="$(values "$LEVEL" 6 "$@" | sort -u)"
  if [ "$(echo "$VERSIONS" | wc -l)" -ne 1 ]; then
    echo "the runs were measured against different versions:" $VERSIONS >&2
    exit 1
  fi
  echo "$LEVEL,$(median "$LEVEL" 2 "$@"),$(median "$LEVEL" 3 "$@"),$(median "$LEVEL" 4 "$@"),$(median "$LEVEL" 5 "$@"),$VERSIONS"
done
