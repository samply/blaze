#!/bin/bash
set -euo pipefail

# This script creates two versions of a binary resource and verifies that the
# binary content of both versions can be read via the vread interaction.

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

base="http://localhost:8080/fhir"

# 10 KiB of random data per version, base64 encoded
data_v1="$(openssl rand 10240 | base64 -w 0)"
data_v2="$(openssl rand 10240 | base64 -w 0)"

id="$(uuidgen | tr '[:upper:]' '[:lower:]')"

binary() {
cat <<END
{
  "resourceType": "Binary",
  "id": "$id",
  "contentType": "application/pdf",
  "data": "$1"
}
END
}

# Create both versions of the Binary resource, keeping their version IDs
version_id_v1=$(binary "$data_v1" | update "$base/Binary/$id" | jq -r '.meta.versionId')
version_id_v2=$(binary "$data_v2" | update "$base/Binary/$id" | jq -r '.meta.versionId')

echo "Created Binary resource with ID $id in versions $version_id_v1 and $version_id_v2"

# Retrieve both versions, Base64 encoding them so they can be safely handled by Bash
base64_encoded_binary_resource_v1=$(curl -sfH 'Accept: application/pdf' "$base/Binary/$id/_history/$version_id_v1" | base64 -w 0)
base64_encoded_binary_resource_v2=$(curl -sfH 'Accept: application/pdf' "$base/Binary/$id/_history/$version_id_v2" | base64 -w 0)

echo "Binary data retrieved. Verifying content..."

if [ "$data_v1" = "$base64_encoded_binary_resource_v1" ]; then
    echo "✅ Base64 encoding of both the Original Data and the Retrieved Resource Data match (version $version_id_v1)"
else
    echo "🆘 Base64 encoding of both the Original Data and the Retrieved Resource Data are different (version $version_id_v1)"
    exit 1
fi

if [ "$data_v2" = "$base64_encoded_binary_resource_v2" ]; then
    echo "✅ Base64 encoding of both the Original Data and the Retrieved Resource Data match (version $version_id_v2)"
else
    echo "🆘 Base64 encoding of both the Original Data and the Retrieved Resource Data are different (version $version_id_v2)"
    exit 1
fi

content_type=$(curl -sf -o /dev/null -w '%{content_type}' -H 'Accept: application/pdf' "$base/Binary/$id/_history/$version_id_v1")

test "Content-Type header of the vread response" "$content_type" "application/pdf"
