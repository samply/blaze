#!/bin/bash
set -euo pipefail

base_url="https://github.com/hapifhir/org.hl7.fhir.core/releases/download/$VALIDATOR_VERSION"
key_file="$(dirname "$0")/../keys/david-otasek.asc"

curl -sSfL "$base_url/validator_cli.jar" > validator_cli.jar
curl -sSfL "$base_url/validator_cli.jar.asc" > validator_cli.jar.asc

# Verify the upstream signature before trusting the pinned checksum. Renovate
# derives VALIDATOR_CHECKSUM from whatever bytes GitHub serves at lookup time,
# so the checksum alone is no evidence that upstream ever produced the JAR. A
# keyring holding only the upstream signing key is used, because gpgv rejects
# signatures made by any key outside of it.
keyring="$(mktemp)"
trap 'rm -f "$keyring"' EXIT
gpg --dearmor < "$key_file" > "$keyring"
gpgv --keyring "$keyring" validator_cli.jar.asc validator_cli.jar

echo "$VALIDATOR_CHECKSUM validator_cli.jar" | sha256sum -c
