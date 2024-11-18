#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
base="${1:-http://localhost:8080/fhir}"

# Upload Resources
find "$script_dir/node_modules" -name "*.json" -and -not -name "package.json" -and -not -name ".package-lock.json" -and -not -name ".index.json" -print0 |\
 xargs -0 -P 4 -I {} "$script_dir/upload.sh" {} "$base"
