#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"

name="$1"

docker run --rm -v "$script_dir":/tls-cert \
  eclipse-temurin:21.0.4_7-jre-jammy@sha256:78d4dd1a43af317620ae19428e126e20fd8a02149f07365371a6baddae18898b \
  keytool -importcert -storetype PKCS12 -keystore "/tls-cert/$name-trust-store.p12" \
  -storepass "insecure" -alias ca -file "/tls-cert/$name-cert.pem" -noprompt
