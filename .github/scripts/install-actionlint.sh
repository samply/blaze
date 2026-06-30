#!/bin/bash
set -euo pipefail

url="https://github.com/rhysd/actionlint/releases/download/v${ACTIONLINT_VERSION}/actionlint_${ACTIONLINT_VERSION}_linux_amd64.tar.gz"
curl -sSfL "$url" > actionlint.tar.gz

tar -xzf actionlint.tar.gz
mkdir -p ~/.local/bin/
mv ./actionlint ~/.local/bin/actionlint
