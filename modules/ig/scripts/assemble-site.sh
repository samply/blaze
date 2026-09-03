#!/bin/bash
set -euo pipefail

# Assembles the site served under https://blaze-server.org/fhir from the
# renderings of the published versions of the guide, which are kept as release
# assets rather than in the repository.
#
# Every version is served under its own prefix, and the newest one additionally
# at the root. package-list.json and history.html index the result. Nothing is
# rendered here: a version is rendered once, by the release that publishes it.
#
# With no archives at all the site is left out entirely, which is the state
# before the first version of the guide has been published.
#
# Usage: assemble-site.sh <archive-dir> <dest-dir>
#
#   archive-dir  directory holding the blaze-ig-<version>.tgz release assets
#   dest-dir     directory to assemble the site in, replaced if it exists

archive_dir="${1:?usage: assemble-site.sh <archive-dir> <dest-dir>}"
dest_dir="${2:?usage: assemble-site.sh <archive-dir> <dest-dir>}"

canonical="https://blaze-server.org/fhir"
ig_resource="ImplementationGuide-blaze.json"

versions=()

for archive in "$archive_dir"/blaze-ig-*.tgz; do
  [ -e "$archive" ] || continue

  version="$(basename "$archive" .tgz)"
  versions+=("${version#blaze-ig-}")
done

if [ ${#versions[@]} -eq 0 ]; then
  echo "No published rendering found, leaving $dest_dir out."
  exit 0
fi

# Newest first, so that the current version heads both indexes.
readarray -t versions < <(printf '%s\n' "${versions[@]}" | sort -Vr)
current="${versions[0]}"

rm -rf "$dest_dir"

for version in "${versions[@]}"; do
  mkdir -p "$dest_dir/$version"
  tar -xzf "$archive_dir/blaze-ig-$version.tgz" -C "$dest_dir/$version"
done

cp -r "$dest_dir/$current"/. "$dest_dir"/

entries="[]"
rows=""

for version in "${versions[@]}"; do
  resource="$dest_dir/$version/$ig_resource"
  date="$(jq -r '.date // empty' "$resource")"
  description="$(jq -r '.description // empty' "$resource")"
  fhir_version="$(jq -r '.fhirVersion[0] // empty' "$resource")"

  entries="$(jq \
    --arg version "$version" \
    --arg date "$date" \
    --arg desc "$description" \
    --arg path "$canonical/$version" \
    --arg fhirversion "$fhir_version" \
    --argjson current "$([ "$version" = "$current" ] && echo true || echo false)" \
    '. + [{
       version: $version,
       date: $date,
       desc: $desc,
       path: $path,
       status: "release",
       sequence: "Releases",
       fhirversion: $fhirversion
     } + (if $current then {current: true} else {} end)]' \
    <<<"$entries")"

  label=""
  [ "$version" = "$current" ] && label=" (current)"

  rows="$rows      <tr><td><a href=\"$version/index.html\">$version</a>$label</td><td>$date</td></tr>"$'\n'
done

jq -n \
  --arg canonical "$canonical" \
  --argjson list "$entries" \
  --slurpfile ig "$dest_dir/$current/$ig_resource" \
  '{
     "package-id": $ig[0].packageId,
     title: $ig[0].title,
     canonical: $canonical,
     introduction: $ig[0].description,
     list: $list
   }' >"$dest_dir/package-list.json"

cat >"$dest_dir/history.html" <<EOF
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Blaze FHIR Server — Directory of Published Versions</title>
</head>
<body>
<h1>Blaze FHIR Server — Directory of Published Versions</h1>
<p>The current version is also served at <a href="index.html">$canonical</a>.</p>
<table>
  <thead>
    <tr><th>Version</th><th>Date</th></tr>
  </thead>
  <tbody>
$rows  </tbody>
</table>
</body>
</html>
EOF

echo "Assembled ${#versions[@]} version(s) in $dest_dir, current is $current."
