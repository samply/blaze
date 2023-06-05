#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

cat "$SCRIPT_DIR/search-type-patient.json" | jq -cM | \
vegeta attack -rate=2000 -max-workers=1000 -format=json -duration=300s | \
vegeta report
