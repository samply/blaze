#!/bin/bash
set -euo pipefail

url="https://github.com/rhysd/actionlint/releases/download/$ACTIONLINT_VERSION/actionlint_${ACTIONLINT_VERSION#v}_linux_amd64.tar.gz"
curl -sSfL "$url" > actionlint.tar.gz
echo "$ACTIONLINT_CHECKSUM actionlint.tar.gz" | sha256sum -c

tar -xzf actionlint.tar.gz
mkdir -p ~/.local/bin/
mv ./actionlint ~/.local/bin/actionlint
