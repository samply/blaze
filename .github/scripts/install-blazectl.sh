#!/usr/bin/env sh

VERSION=0.5.0

curl -sLO https://github.com/samply/blazectl/releases/download/v${VERSION}/blazectl-${VERSION}-linux-amd64.tar.gz
tar xzf blazectl-${VERSION}-linux-amd64.tar.gz
rm blazectl-${VERSION}-linux-amd64.tar.gz
