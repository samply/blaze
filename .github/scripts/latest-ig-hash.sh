#!/bin/bash
set -euo pipefail

# Prints the artifact digest of the most recently published rendering of the
# Implementation Guide, or nothing if none has been published yet. The release
# workflow compares it against the digest of the release being published and
# renders the guide only when the two differ.
#
# Usage: latest-ig-hash.sh

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

tags="$(gh release list --exclude-drafts --exclude-pre-releases --limit 200 --json tagName --jq '.[].tagName')"

for tag in $tags; do
  if gh release download "$tag" --pattern 'blaze-ig-*.tgz' --dir "$work_dir" 2>/dev/null; then
    archive="$(find "$work_dir" -name 'blaze-ig-*.tgz' | head -n 1)"
    tar -xzf "$archive" -C "$work_dir" ./artifact-hash.txt 2>/dev/null || exit 0
    cat "$work_dir/artifact-hash.txt"
    exit 0
  fi
done
