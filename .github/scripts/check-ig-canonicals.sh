#!/bin/bash
set -euo pipefail

# Checks that every canonical Blaze emits under its canonical base is defined in
# the Implementation Guide, so that it resolves at its URL. The failure mode this
# guards against is a new profile, extension, code system or operation being
# introduced in the code without its definition in the guide.
#
# The canonicals are taken from the source: Blaze builds them from a path like
# "CodeSystem/JobType" relative to the base, or spells them out in full. The
# paths the guide resolves are the ones of the index the IG resolver is built
# from.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

index="${1:-modules/ig-resolver/target/index.json}"

base="https://blaze-server.org/fhir"
types="CodeSystem|ValueSet|StructureDefinition|OperationDefinition|SearchParameter|GraphDefinition|NamingSystem|sid"

# Prints the paths, relative to the canonical base, of all canonicals Blaze
# emits. Ids don't end in a dot, so a canonical closing a sentence in a
# documentation string keeps its id.
emitted-paths() {
  {
    git grep -ohE "\"($types)/[A-Za-z0-9._-]+\"" -- 'modules/*/src/**.clj' 'modules/*/src/**.java' | tr -d '"'
    git grep -ohE "${base//./\\.}/($types)/[A-Za-z0-9._-]+" -- 'modules/*/src/**.clj' 'modules/*/src/**.java' 'resources/blaze.edn' |
      sed "s|^$base/||"
  } | sed 's|\.$||' | sort -u
}

resolved_paths="$(jq -r '.resources | keys[]' "$index")"

checked=0
missing=()
while read -r path; do
  checked=$((checked + 1))
  if ! grep -qxF -- "$path" <<< "$resolved_paths"; then
    missing+=("$base/$path")
  fi
done < <(emitted-paths)

echo "ℹ️  checked $checked canonicals against $index"

test_not_equal "number of checked canonicals" "$checked" 0
test_empty "list of canonicals not defined in the Implementation Guide" "${missing[*]-}"
