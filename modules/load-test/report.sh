#!/bin/bash -e

results_file="$1"

jq -f http-req-duration.jq "$results_file"
