#!/bin/bash
set -euo pipefail

# This script creates one large Binary resource via JSON and one via XML and
# verifies that the binary content of both resources can be read back unchanged.
#
# The first argument is the size of the random data in MiB. Because that data is
# Base64 encoded inside the Binary resources, the request bodies are about 4/3
# of that size.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

size="$1"
base="http://localhost:8080/fhir"

data_file=$(mktemp)
json_file=$(mktemp)
xml_file=$(mktemp)
download_file=$(mktemp)

# Ensure cleanup of temporary files
trap 'rm -f "$data_file" "$json_file" "$xml_file" "$download_file"' EXIT

# Creates a Binary resource from the file $2, using the content type $1, and
# returns the URL of the created version, taken from the Location header of the
# response.
create_binary() {
  curl -sf -o /dev/null -D - -H "Content-Type: $1" -H 'Prefer: return=minimal' --data-binary "@$2" "$base/Binary" |
    grep -i '^location:' | tr -d '\r' | sed 's/^[^:]*: *//'
}

# Reads the binary content of the Binary resource at the URL $2, which was
# created via $1, and verifies that it's identical to the original data.
check_binary_content() {
  curl -sfH 'Accept: application/octet-stream' "$2" > "$download_file"

  if cmp -s "$data_file" "$download_file"; then
    echo "✅ the binary content of the Binary resource created via $1 is identical to the original data"
  else
    echo "🆘 the binary content of the Binary resource created via $1 differs from the original data"
    echo "original size   : $(wc -c < "$data_file") bytes"
    echo "downloaded size : $(wc -c < "$download_file") bytes"
    exit 1
  fi
}

# Random data of $size MiB
openssl rand "$((size * 1024 * 1024))" > "$data_file"

# Both resources carry that data Base64 encoded into a single line
{ printf '{"resourceType": "Binary", "data": "'; base64 -w 0 < "$data_file"; printf '"}'; } > "$json_file"
{ printf '<Binary xmlns="http://hl7.org/fhir"><data value="'; base64 -w 0 < "$data_file"; printf '"/></Binary>'; } > "$xml_file"

json_url="$(create_binary 'application/fhir+json' "$json_file")"
xml_url="$(create_binary 'application/fhir+xml' "$xml_file")"

echo "Created Binary resources with $size MiB of data"
echo "  - via JSON, at: $json_url"
echo "  - via XML, at: $xml_url"

check_binary_content JSON "$json_url"
check_binary_content XML "$xml_url"
