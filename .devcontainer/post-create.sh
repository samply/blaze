#!/bin/bash
# postCreateCommand of the dev container: prepare the workspace.
#
# Runs once, after the container has been created. Builds the Blaze
# implementation guide, whose generated resources several modules need for
# their tests, and downloads the dependencies of the root project.
#
# The dependencies of the individual modules are downloaded on demand. Run
# `make deps-prep` to download them all at once.

set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Building the implementation guide (make build-ig)"
make build-ig

echo "==> Downloading the dependencies of the root project (make prep)"
make prep

cat <<'EOF'

The dev container is ready:

  make fmt                     check the formatting
  make lint                    lint the code
  make -C modules/<module> test    run the tests of a module
  make emacs-repl              start an nREPL server for the whole system

To use the Claude Code CLI inside the container, install it once with
`npm install -g @anthropic-ai/claude-code`. It is installed into the home
directory, which is part of the container, but its configuration in
~/.claude survives a rebuild.
EOF
