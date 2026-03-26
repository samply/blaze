#!/bin/bash
set -euo pipefail

curl -sLO "https://github.com/hapifhir/org.hl7.fhir.core/releases/download/$VALIDATOR_VERSION/validator_cli.jar"
echo "$VALIDATOR_CHECKSUM validator_cli.jar" | sha256sum -c
