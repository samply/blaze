#!/bin/bash
set -euo pipefail

url="https://github.com/grafana/dashboard-linter/releases/download/$DASHBOARD_LINTER_VERSION/dashboard-linter_${DASHBOARD_LINTER_VERSION#v}_linux_amd64.tar.gz"
curl -sSfL "$url" > dashboard-linter.tar.gz
echo "$DASHBOARD_LINTER_CHECKSUM dashboard-linter.tar.gz" | sha256sum -c

tar -xzf dashboard-linter.tar.gz
mkdir -p ~/.local/bin/
mv ./dashboard-linter ~/.local/bin/dashboard-linter
