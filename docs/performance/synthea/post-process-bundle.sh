#!/bin/sh -e

jq -f post-process-bundles.jq "$1" >"$1.tmp"
rm "$1"
mv "$1.tmp" "$1"
gzip "$1"
