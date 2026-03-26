#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"

jq -cM < "$script_dir/search-type-patient.json" | \
vegeta attack -rate=2000 -max-workers=1000 -format=json -duration=300s | \
vegeta report
