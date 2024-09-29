#!/bin/bash -e

RESULTS_FILE="$1"

jq -f http-req-duration.jq "$RESULTS_FILE"
