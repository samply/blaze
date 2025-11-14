#!/bin/bash -e

# generate random data as Base64
data=$(openssl rand -base64 "$1")

echo "{\"resourceType\": \"Binary\", \"data\": \"$data\"}" > large-binary.json
echo "<Binary xmlns=\"http://hl7.org/fhir\"><data value=\"$data\"/></Binary>" > large-binary.xml
