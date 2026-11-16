#!/bin/bash
set -euo pipefail

# Runs the transaction load test of the given system against one Blaze version.
#
# With a release version like 1.10.0 the Blaze of the system is started from the
# image samply/blaze:<version>. Without a version it is started from the
# development build blaze:latest and the version is the one the server reports,
# the commit hash of that build.
#
# For every run, the Blaze of the system is restarted with an empty database via
# reset.sh and the sweep of transaction.js is executed. The runs are written to
# data/raw/transaction/<system>/<version>-<run>.csv and their median to
# data/transaction/<system>/<version>.csv.
#
# At most one development run is kept per system. So after the new median is
# written, the medians and runs of older development versions are deleted,
# unless the new version is a release. A release run instead deletes the
# development run of the system whose commit is an ancestor of the release tag
# v<version>, because the release contains that commit. If git doesn't know the
# commit or the tag, like without fetched tags, the development run is kept and
# a warning is printed. A failed run leaves everything as it was.
#
# The environment variable IMAGE overrides the image, like samply/blaze:main for
# the current main branch. The version stays the one the server reports, so the
# results are named correctly whatever image runs. BASE is the FHIR base URL of
# Blaze and defaults to http://localhost:8080/fhir. RUNS are the names of the
# runs and default to "1 2 3". The median needs an odd number of them.
#
# Usage: ./transaction.sh <system> [<version>]

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
SYSTEM="$1"
VERSION="${2:-}"
BASE="${BASE:-http://localhost:8080/fhir}"
RUNS="${RUNS:-1 2 3}"

# Succeeds if the version is a release. Every other version is the commit hash
# of a development build.
is_release() {
  [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

# Succeeds if the version is the commit hash of a development build.
is_commit() {
  [[ "$1" =~ ^[0-9a-f]{7,40}$ ]]
}

# Deletes the median and the runs of the given version of the system.
delete_version() {
  echo "deleting the run $1"
  rm -f "$MEDIAN_DIR/$1.csv" "$RAW_DIR/$1"-*.csv
}

# Prints the versions of the commit runs of the system.
commit_versions() {
  for FILE in "$MEDIAN_DIR"/*.csv; do
    [ -e "$FILE" ] || continue
    OLD="$(basename "$FILE" .csv)"
    if ! is_release "$OLD"; then
      echo "$OLD"
    fi
  done
}

if [ -n "$VERSION" ]; then
  export IMAGE="${IMAGE:-samply/blaze:$VERSION}"
else
  export IMAGE="${IMAGE:-blaze:latest}"
fi

cd "$SCRIPT_DIR"

MEDIAN_DIR="data/transaction/$SYSTEM"
RAW_DIR="data/raw/transaction/$SYSTEM"
mkdir -p "$MEDIAN_DIR" "$RAW_DIR"

for RUN in $RUNS; do
  ./reset.sh "$SYSTEM"

  if [ -z "$VERSION" ]; then
    VERSION="$(curl -sSf -H 'Accept: application/fhir+json' "$BASE/metadata?_elements=software" |
      jq -r '.software.version // empty')"

    if ! is_release "$VERSION" && ! is_commit "$VERSION"; then
      echo "the software version '$VERSION' of $BASE is neither a release nor a commit hash" >&2
      exit 1
    fi
  fi

  SYSTEM="$SYSTEM" RUN="$RUN" VERSION="$VERSION" BASE="$BASE" k6 run transaction.js
done

RAW_FILES=()
for RUN in $RUNS; do
  RAW_FILES+=("$RAW_DIR/$VERSION-$RUN.csv")
done

# written to a temporary file first, so that a failing median leaves no median
# file and no temporary file
MEDIAN_FILE="$MEDIAN_DIR/$VERSION.csv"
if ! ./median.sh "${RAW_FILES[@]}" > "$MEDIAN_FILE.tmp"; then
  rm -f "$MEDIAN_FILE.tmp"
  exit 1
fi
mv "$MEDIAN_FILE.tmp" "$MEDIAN_FILE"

if is_release "$VERSION"; then
  for OLD in $(commit_versions); do
    if git merge-base --is-ancestor "$OLD" "v$VERSION" 2>/dev/null; then
      delete_version "$OLD"
    elif [ $? -ne 1 ]; then
      echo "warning: git doesn't know the commit $OLD or the tag v$VERSION, so the commit run $OLD of $SYSTEM was kept; check with: git fetch --tags" >&2
    fi
  done
else
  for OLD in $(commit_versions); do
    if [ "$OLD" != "$VERSION" ]; then
      delete_version "$OLD"
    fi
  done
fi
