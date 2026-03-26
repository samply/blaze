#!/bin/bash
set -euo pipefail

# This script runs ShellCheck on all shell scripts in the repository and
# verifies that they all have strict mode enabled (set -euo pipefail).

# Find all .sh files, excluding node_modules and .git
scripts=$(find . -name "*.sh" -not -path "*/node_modules/*" -not -path "*/.git/*")

if [ -z "$scripts" ]; then
  echo "No shell scripts found."
  exit 0
fi

echo "Running ShellCheck on shell scripts..."
# shellcheck disable=SC2086
shellcheck -x $scripts

echo "Checking for strict mode (set -euo pipefail) in shell scripts..."
missing_strict_mode=""
for script in $scripts; do
  if ! grep -q "set -euo pipefail" "$script"; then
    missing_strict_mode="$missing_strict_mode $script"
  fi
done

if [ -n "$missing_strict_mode" ]; then
  echo "The following scripts are missing 'set -euo pipefail':"
  for script in $missing_strict_mode; do
    echo "  $script"
  done
  exit 1
fi

echo "All shell scripts passed."
