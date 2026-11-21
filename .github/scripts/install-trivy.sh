#!/bin/bash
set -euo pipefail

url="https://github.com/aquasecurity/trivy/releases/download/$TRIVY_VERSION/trivy_${TRIVY_VERSION#v}_Linux-64bit.tar.gz"
curl -sSfL "$url" > trivy.tar.gz
echo "$TRIVY_CHECKSUM trivy.tar.gz" | sha256sum -c

tar -xzf trivy.tar.gz trivy
mkdir -p ~/.local/bin/
mv ./trivy ~/.local/bin/trivy
