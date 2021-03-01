#!/usr/bin/env -S bash -e

SOFTWARE_NAME=$(curl -s http://localhost:8080/fhir/metadata | jq -r .software.name)

if [ "Blaze" = $SOFTWARE_NAME ]; then
  echo "Success"
  exit 0
else
  echo "Fail"
  exit 1
fi
