#!/bin/bash -e

# generate 8 MiB random data as Base64
DATA=$(openssl rand -base64 8388608)

echo "{\"resourceType\": \"Binary\", \"data\": \"$DATA\"}" > large-binary.json
echo "<Binary xmlns=\"http://hl7.org/fhir\"><data value=\"$DATA\"/></Binary>" > large-binary.xml
