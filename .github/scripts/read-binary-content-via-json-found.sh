#!/bin/bash -e

# This script creates a binary resource and verifies that its binary content
# can be read (via JSON).

base="http://localhost:8080/fhir"

# 10 KiB of random data, base64 encoded
data="$(openssl rand -base64 10240 | tr -d '\n')"

binary() {
cat <<END
{
  "resourceType": "Binary",
  "contentType": "application/pdf",
  "data": "$data"
}
END
}

# Create a Binary resource that contains that data, and get its ID (via JSON)
id_via_json=$(curl -s -H 'Content-Type: application/fhir+json' -d "$(binary)" "$base/Binary" | jq -r '.id')

echo "Created Binary resource that contains the Random Data"
echo "  - via JSON, with ID: $id_via_json"


# Retrieve the Binary resource, and Base64 encode it so it can be safely handled by Bash (JSON)
base64_encoded_binary_resource_via_json=$(curl -s -H 'Accept: application/pdf' "$base/Binary/$id_via_json" | base64 | tr -d '\n')


echo "Binary data retrieved. Verifying content... (JSON version)"

if [ "$data" = "$base64_encoded_binary_resource_via_json" ]; then
    echo "✅ Base64 encoding of both the Original Data and the Retrieved Resource Data match (JSON)"
else
    echo "🆘 Base64 encoding of both the Original Data and the Retrieved Resource Data are different (JSON)"
    exit 1
fi
