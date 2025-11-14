#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"

name="$1"
base="$2"

docker run --rm -v "$script_dir":/tls-cert --entrypoint openssl alpine/openssl \
  req -nodes -subj "/CN=$base" -addext "subjectAltName = DNS:$base" \
  -x509 -newkey rsa:4096 -keyout /tls-cert/$name-key.pem \
  -out /tls-cert/$name-cert.pem -days 30
