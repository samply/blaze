#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
DIR="$1"

# Upload Resources
find "$DIR" -name "*.json" -and -not -name "package.json" -and -not -name ".package-lock.json" -and -not -name ".index.json" -print0 |\
 xargs -0 -P 4 -I {} "$SCRIPT_DIR/upload-definition.sh" {}
