#!/bin/bash -e

#
# Adds module path prefix to the paths in codecov.json files.
#

MODULE="$1"
FILE="modules/$MODULE/target/coverage/codecov.json"

jq --arg prefix "modules/$MODULE/src/" '.coverage |= with_entries(.key = $prefix + .key)' "$FILE" > "$FILE.fix"
mv "$FILE.fix" "$FILE"
