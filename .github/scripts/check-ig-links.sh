#!/bin/bash
set -euo pipefail

# Checks that every link from the documentation into the Implementation Guide
# points to a file of the rendering. VitePress doesn't check these links itself,
# because they carry target="_self" to bypass its router, and the rendering is
# copied into the site only after VitePress built it.
#
# Usage: check-ig-links.sh <dist-dir>

dist="$1"

links="$(grep -rhoE --include='*.html' --exclude-dir=fhir 'href="/fhir/[^"#?]*' "$dist" | sed 's|^href="||' | sort -u || true)"

if [ -z "$links" ]; then
  echo "🆘 found no links into the Implementation Guide in $dist"
  exit 1
fi

broken=0
while IFS= read -r link; do
  file="$dist$link"
  [[ "$file" == */ ]] && file="${file}index.html"
  if [ -f "$file" ]; then
    echo "✅ $link resolves"
  else
    echo "🆘 $link doesn't resolve"
    broken=1
  fi
done <<< "$links"

exit "$broken"
