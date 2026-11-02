#!/bin/bash
set -euo pipefail

# Checks that every commit in the range from the base commit $1 (exclusive) to
# the head commit $2 was committed by its author. Fails otherwise, because the
# signature of such a commit is the signature of its committer, so it doesn't
# confirm the consent of its author. GitHub shows such commits as "Partially
# verified", but the rule requiring signed commits accepts them.
#
# Commits created by GitHub itself, like merge commits, web edits and commits
# of bots like Renovate, are allowed.

base="$1"
head="$2"

github_committer="noreply@github.com"
failed=0

while IFS=$'\t' read -r sha author committer; do
  if [ "$committer" = "$github_committer" ] || [ "${author,,}" = "${committer,,}" ]; then
    echo "✅ the commit $sha authored by $author was committed by $committer"
  else
    echo "🆘 the commit $sha authored by $author was committed by $committer, expected its author"
    failed=1
  fi
done < <(git log --format=$'%H\t%ae\t%ce' "$base..$head")

exit "$failed"
