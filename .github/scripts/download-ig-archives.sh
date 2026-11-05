#!/bin/bash
set -euo pipefail

# Downloads every published rendering of the Implementation Guide. A release
# that changes the conformance artifacts carries its rendering as an asset,
# which keeps every published version reachable without committing generated
# files or rebuilding them.
#
# Releases that did not change the artifacts, and those from before the guide
# was published this way, have no such asset and are skipped.
#
# Usage: download-ig-archives.sh <dest-dir>

dest_dir="${1:?usage: download-ig-archives.sh <dest-dir>}"

mkdir -p "$dest_dir"

tags="$(gh release list --exclude-drafts --exclude-pre-releases --limit 200 --json tagName --jq '.[].tagName')"

for tag in $tags; do
  gh release download "$tag" --pattern 'blaze-ig-*.tgz' --dir "$dest_dir" --clobber ||
    echo "No Implementation Guide asset on $tag, skipping."
done

echo "Downloaded $(find "$dest_dir" -name 'blaze-ig-*.tgz' | wc -l) archived rendering(s)."
