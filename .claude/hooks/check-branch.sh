#!/bin/bash
# SessionStart hook: warn if the checked out branch is behind its remote.
#
# A cloud session can start on a stale checkout of its branch: the startup may
# check out an older local branch from a previously used disk and move the
# remote-tracking ref back to it, so that `git status` reports "up to date"
# although the branch was rewritten on the remote in the meantime.
#
# This fetches the current branch from origin and prints a warning into the
# session context if the local branch is behind or has diverged. It never
# changes the checkout, because the local branch might hold unpushed work.

set -euo pipefail

# Only run in the remote (Claude Code on the web) environment.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}"

branch="$(git symbolic-ref --quiet --short HEAD)" || exit 0

fetch_failed() {
  echo "warning: could not fetch branch ${branch} from origin; check whether it is up to date before starting work."
  exit 0
}

# A network problem must not block the session start.
remote_head="$(timeout 60 git ls-remote --heads origin "refs/heads/${branch}" 2>/dev/null)" || fetch_failed

# A branch that doesn't exist on the remote yet can't be behind it.
[ -n "${remote_head}" ] || exit 0

timeout 60 git fetch --quiet origin "+refs/heads/${branch}:refs/remotes/origin/${branch}" 2>/dev/null || fetch_failed

read -r ahead behind < <(git rev-list --left-right --count "HEAD...origin/${branch}")

if [ "${behind}" -gt 0 ]; then
  if [ "${ahead}" -gt 0 ]; then
    state="has diverged from origin/${branch} (${ahead} local and ${behind} remote commits)"
  else
    state="is behind origin/${branch} (${behind} remote commits missing)"
  fi
  echo "WARNING: The local branch ${branch} ${state}. The remote branch might have been rewritten after this checkout was made. Reconcile the branch with origin/${branch} before starting work, without discarding the work of either side."
fi
