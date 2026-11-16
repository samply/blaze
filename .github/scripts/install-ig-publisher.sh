#!/bin/bash
set -euo pipefail

base_url="https://github.com/HL7/fhir-ig-publisher/releases/download/$IG_PUBLISHER_VERSION"
key_file="$(dirname "$0")/../keys/david-otasek.asc"

curl -sSfL "$base_url/publisher.jar" > publisher.jar
curl -sSfL "$base_url/publisher.jar.asc" > publisher.jar.asc

# Verify the upstream signature before trusting the pinned checksum. Renovate
# derives IG_PUBLISHER_CHECKSUM from whatever bytes GitHub serves at lookup time,
# so the checksum alone is no evidence that upstream ever produced the JAR. A
# keyring holding only the upstream signing key is used, because gpgv rejects
# signatures made by any key outside of it. The publisher is signed by the same
# key as the validator.
keyring="$(mktemp)"
trap 'rm -f "$keyring"' EXIT
gpg --dearmor < "$key_file" > "$keyring"
gpgv --keyring "$keyring" publisher.jar.asc publisher.jar

echo "$IG_PUBLISHER_CHECKSUM publisher.jar" | sha256sum -c
