#!/bin/bash -e

url="https://github.com/samply/blazectl/releases/download/${BLAZECTL_VERSION}/blazectl-${BLAZECTL_VERSION#v}-linux-amd64.tar.gz"
curl -sSfL "${url}" >blazectl.tar.gz
echo "${BLAZECTL_CHECKSUM} blazectl.tar.gz" | sha256sum -c

tar -xzf blazectl.tar.gz
mkdir -p ~/.local/bin/
mv blazectl ~/.local/bin/blazectl
