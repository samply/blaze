#!/bin/bash -e

curl -sSfL https://raw.githubusercontent.com/samply/blazectl/main/install.sh |
  sh -s "$BLAZECTL_VERSION"

sudo mv ./blazectl /usr/local/bin/blazectl
