#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

NAME="$1"

docker run --rm -v "$SCRIPT_DIR":/tls-cert \
  eclipse-temurin:21.0.4_7-jre-jammy@sha256:78d4dd1a43af317620ae19428e126e20fd8a02149f07365371a6baddae18898b \
  keytool -importcert -storetype PKCS12 -keystore "/tls-cert/$NAME-trust-store.p12" \
  -storepass "insecure" -alias ca -file "/tls-cert/$NAME-cert.pem" -noprompt
