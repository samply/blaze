#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

NAME="$1"
BASE="$2"

docker run --rm -v "$SCRIPT_DIR":/tls-cert --entrypoint openssl alpine/openssl \
  req -nodes -subj "/CN=$BASE" -addext "subjectAltName = DNS:$BASE" \
  -x509 -newkey rsa:4096 -keyout /tls-cert/$NAME-key.pem \
  -out /tls-cert/$NAME-cert.pem -days 30
