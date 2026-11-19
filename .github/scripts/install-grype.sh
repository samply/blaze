#!/bin/bash
set -euo pipefail

url="https://github.com/anchore/grype/releases/download/$GRYPE_VERSION/grype_${GRYPE_VERSION#v}_linux_amd64.tar.gz"
curl -sSfL "$url" > grype.tar.gz
echo "$GRYPE_CHECKSUM grype.tar.gz" | sha256sum -c

tar -xzf grype.tar.gz grype
mkdir -p ~/.local/bin/
mv ./grype ~/.local/bin/grype
