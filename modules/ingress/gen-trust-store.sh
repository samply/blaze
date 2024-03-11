#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

NAME="$1"

docker run --rm -v "$SCRIPT_DIR":/tls-cert \
  eclipse-temurin:17-jre-jammy@sha256:c1dd17f6b753f0af572069fb80dcacdc687f5d1ae3e05c98d5f699761b84a10a \
  keytool -importcert -storetype PKCS12 -keystore "/tls-cert/$NAME-trust-store.p12" \
  -storepass "insecure" -alias ca -file "/tls-cert/$NAME-cert.pem" -noprompt
