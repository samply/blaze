#!/bin/bash -e

version="1.7.7"

curl -sLO "https://github.com/rhysd/actionlint/releases/download/v${version}/actionlint_${version}_linux_amd64.tar.gz"
tar xzf "actionlint_${version}_linux_amd64.tar.gz"
rm "actionlint_${version}_linux_amd64.tar.gz"
sudo mv ./actionlint /usr/local/bin/actionlint
