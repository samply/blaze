#!/bin/bash -e

VERSION="1.7.7"

curl -sLO "https://github.com/rhysd/actionlint/releases/download/v${VERSION}/actionlint_${VERSION}_linux_amd64.tar.gz"
tar xzf "actionlint_${VERSION}_linux_amd64.tar.gz"
rm "actionlint_${VERSION}_linux_amd64.tar.gz"
sudo mv ./actionlint /usr/local/bin/actionlint
