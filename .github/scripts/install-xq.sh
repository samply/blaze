#!/bin/bash
set -euo pipefail

sudo apt-get -o Acquire::Retries=5 install -y xq
