#!/bin/bash
set -euo pipefail

# Checks that the Blaze service blaze-legacy of the openid-auth-test logs the
# legacy mode warning and the warnings about the missing issuer and audience.

logs=$(docker compose -f .github/openid-auth-test/docker-compose.yml logs blaze-legacy)

for msg in \
  "OPENID_LEGACY_MODE is enabled. The legacy mode is deprecated" \
  "OPENID_PROVIDER_ISSUER is not set; issuer (iss) claim will not be validated." \
  "OPENID_AUDIENCE is not set; audience (aud) claim will not be validated."; do
  if grep -qF "$msg" <<< "$logs"; then
    echo "✅ the log contains: $msg"
  else
    echo "🆘 the log doesn't contain: $msg"
    exit 1
  fi
done
