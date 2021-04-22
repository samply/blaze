#!/bin/sh

curl -s --oauth2-bearer $ACCESS_TOKEN http://localhost:8080/fhir | jq -S .
