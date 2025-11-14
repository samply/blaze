#!/bin/bash -e

# This script creates a binary resource and verifies that its binary content
# can be read (via XML).

base="http://localhost:8080/fhir"

# 10 KiB of random data, base64 encoded
data="$(openssl rand -base64 10240 | tr -d '\n')"

binary() {
cat <<END
<Binary xmlns="http://hl7.org/fhir">
  <contentType value="application/pdf"/>
  <data value="$data"/>
</Binary>
END
}

# Create a Binary resource that contains that data, and get its ID (via XML)
id_via_xml=$(curl -s -H 'Content-Type: application/fhir+xml' -H 'Accept: application/fhir+xml' -d "$(binary)" "$base/Binary" | xq -x //id/@value)

echo "Created Binary resource that contains the Random Data"
echo "  - via XML, with ID: $id_via_xml"

# Retrieve the Binary resource, and Base64 encode it so it can be safely handled by Bash (via XML)
base64_encoded_binary_resource_via_xml=$(curl -s -H 'Accept: application/pdf' "$base/Binary/$id_via_xml" | base64 | tr -d '\n')

echo "Binary data retrieved. Verifying content... (XML version)"

if [ "$data" = "$base64_encoded_binary_resource_via_xml" ]; then
    echo "✅ Base64 encoding of both the Original Data and the Retrieved Resource Data match (XML)"
else
    echo "🆘 Base64 encoding of both the Original Data and the Retrieved Resource Data are different (XML)"
    exit 1
fi
