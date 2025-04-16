#!/bin/bash -e

# generate random data as Base64
DATA=$(openssl rand -base64 "$1")

echo "{\"resourceType\": \"Binary\", \"data\": \"$DATA\"}" > large-binary.json
echo "<Binary xmlns=\"http://hl7.org/fhir\"><data value=\"$DATA\"/></Binary>" > large-binary.xml
