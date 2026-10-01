#!/bin/bash
set -euo pipefail

# Prints a digest over the conformance artifacts the guide defines. A new
# version of the guide is only published when this digest changes, so that the
# site does not accumulate an identical rendering per Blaze release.
#
# ImplementationGuide-blaze.json is excluded because it is the only generated
# resource carrying the version and the date, both of which are stamped from the
# release and would make every build look like a change. No other generated
# resource has a version field, so the remaining files are byte-identical unless
# the FSH sources actually changed.
#
# Usage: artifact-hash.sh <resource-dir>

resource_dir="${1:?usage: artifact-hash.sh <resource-dir>}"

find "$resource_dir" -maxdepth 1 -name '*.json' ! -name 'ImplementationGuide-blaze.json' -print0 |
  sort -z |
  xargs -0 sha256sum |
  sed "s|$resource_dir/||" |
  sha256sum |
  cut -d' ' -f1
