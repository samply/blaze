#!/bin/bash -e

# This script creates a binary resource and verifies that its binary content
# can be read (via XML).

BASE="http://localhost:8080/fhir"

# 10 KiB of random data, base64 encoded
DATA="$(openssl rand -base64 10240 | tr -d '\n')"

binary() {
cat <<END
<Binary xmlns="http://hl7.org/fhir">
  <contentType value="application/pdf"/>
  <data value="$DATA"/>
</Binary>
END
}


# Create a Binary resource that contains that data, and get its ID (via XML)
ID_VIA_XML=$(curl -s -H 'Content-Type: application/fhir+xml' -H 'Accept: application/fhir+xml' -d "$(binary)" "$BASE/Binary" | xq -x //id/@value)

echo "Created Binary resource that contains the Random Data"
echo "  - via XML, with ID: $ID_VIA_XML"


# Retrieve the Binary resource, and Base64 encode it so it can be safely handled by Bash (via XML)
BASE64_ENCODED_BINARY_RESOURCE_VIA_XML=$(curl -s -H 'Accept: application/pdf' "$BASE/Binary/$ID_VIA_XML" | base64 | tr -d '\n')


echo "Binary data retrieved. Verifying content... (XML version)"

if [ "$DATA" = "$BASE64_ENCODED_BINARY_RESOURCE_VIA_XML" ]; then
    echo "✅ Base64 encoding of both the Original Data and the Retrieved Resource Data match (XML)"
else
    echo "🆘 Base64 encoding of both the Original Data and the Retrieved Resource Data are different (XML)"
    exit 1
fi
