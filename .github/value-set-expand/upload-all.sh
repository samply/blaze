#!/bin/bash
set -euo pipefail

# Uploads all CodeSystem and ValueSet resources of the FHIR packages installed
# in the directory $2 into the server $1.

script_dir="$(dirname "$(readlink -f "$0")")"
base="${1:-http://localhost:8080/fhir}"
package_dir="${2:-$script_dir/node_modules}"

# Upload Resources
find "$package_dir" -name "*.json" -and -not -name "package.json" -and -not -name ".package-lock.json" -and -not -name ".index.json" -print0 |\
 xargs -0 -P 4 -I {} "$script_dir/upload.sh" {} "$base"

num_code_systems="$(curl -s "$base/metadata?mode=terminology" | jq -r '.codeSystem | length')"

echo
echo "Successfully uploaded CodeSystem and ValueSet resources with a total of $num_code_systems code systems available now."
