#!/bin/bash
set -euo pipefail

# Scans an image with Trivy or Grype applying a VEX document and checks that
# the given vulnerability is suppressed by it.
#
# Both scanners list a suppressed finding separately from the regular ones. So
# a finding listed as suppressed proves two things at once: that the scanner
# still detects the vulnerability and that the VEX document matches it. A
# vulnerability vanishing from the scanner database fails this check instead
# of passing it without testing anything.
#
# Usage: check-vex-suppression.sh <trivy|grype> <image> <vex-document> <vulnerability-id>

scanner="$1"
image="$2"
document="$3"
id="$4"

report="$(mktemp)"

case "$scanner" in
  trivy)
    trivy image --quiet --scanners vuln --format json --vex "$document" --show-suppressed "$image" > "$report"
    findings="$(jq -r '.Results[]?.Vulnerabilities[]?.VulnerabilityID' "$report")"
    suppressed="$(jq -r '.Results[]?.ExperimentalModifiedFindings[]? | select(.Status == "not_affected") | .Finding.VulnerabilityID' "$report")"
    ;;
  grype)
    grype --quiet --output json --vex "$document" "$image" > "$report"
    findings="$(jq -r '.matches[]?.vulnerability.id' "$report")"
    suppressed="$(jq -r '.ignoredMatches[]? | select(any(.appliedIgnoreRules[]?; .["vex-status"] == "not_affected")) | .vulnerability.id' "$report")"
    ;;
  *)
    echo "🆘 unknown scanner $scanner"
    exit 1
    ;;
esac

if grep -qxF "$id" <<< "$suppressed"; then
  echo "✅ $scanner lists $id as suppressed by VEX in $image"
else
  echo "🆘 $scanner doesn't list $id as suppressed by VEX in $image"
  exit 1
fi

if grep -qxF "$id" <<< "$findings"; then
  echo "🆘 $scanner still reports $id as a finding in $image"
  exit 1
else
  echo "✅ $scanner doesn't report $id as a finding in $image"
fi
