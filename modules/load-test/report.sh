#!/bin/bash
set -euo pipefail

results_file="$1"

jq -f http-req-duration.jq "$results_file"
