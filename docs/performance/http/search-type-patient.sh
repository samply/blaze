#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"

cat "$script_dir/search-type-patient.json" | jq -cM | \
vegeta attack -rate=2000 -max-workers=1000 -format=json -duration=300s | \
vegeta report
