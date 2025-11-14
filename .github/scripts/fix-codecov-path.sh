#!/bin/bash -e

#
# Adds module path prefix to the paths in codecov.json files.
#

module="$1"
file="modules/$module/target/coverage/codecov.json"

jq --arg prefix "modules/$module/src/" '.coverage |= with_entries(.key = $prefix + .key)' "$file" > "$file.fix"
mv "$file.fix" "$file"
