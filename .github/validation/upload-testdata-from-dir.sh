#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
DIR="$1"

find "$DIR" -name "*.json" -and -not -name "Bundle*.json" -and -not -name "package.json" -and -not -name ".package-lock.json" -and -not -name ".index.json" -print0 |\
 xargs -0 -I {} "$SCRIPT_DIR/upload-testdata.sh" {}
