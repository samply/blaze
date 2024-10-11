#!/bin/bash -e

# This script creates a binary resource and verifies that its binary content
# can be read (via JSON).

BASE="http://localhost:8080/fhir"

# 10 KiB of random data, base64 encoded
DATA="$(openssl rand -base64 10240 | tr -d '\n')"

binary() {
cat <<END
{
  "resourceType": "Binary",
  "contentType": "application/pdf",
  "data": "$DATA"
}
END
}

# Create a Binary resource that contains that data, and get its ID (via JSON)
ID_VIA_JSON=$(curl -s -H 'Content-Type: application/fhir+json' -d "$(binary)" "$BASE/Binary" | jq -r '.id')

echo "Created Binary resource that contains the Random Data"
echo "  - via JSON, with ID: $ID_VIA_JSON"


# Retrieve the Binary resource, and Base64 encode it so it can be safely handled by Bash (JSON)
BASE64_ENCODED_BINARY_RESOURCE_VIA_JSON=$(curl -s -H 'Accept: application/pdf' "$BASE/Binary/$ID_VIA_JSON" | base64 | tr -d '\n')


echo "Binary data retrieved. Verifying content... (JSON version)"

if [ "$DATA" = "$BASE64_ENCODED_BINARY_RESOURCE_VIA_JSON" ]; then
    echo "✅ Base64 encoding of both the Original Data and the Retrieved Resource Data match (JSON)"
else
    echo "🆘 Base64 encoding of both the Original Data and the Retrieved Resource Data are different (JSON)"
    exit 1
fi
