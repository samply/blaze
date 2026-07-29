#!/bin/bash
# PreToolUse hook: gate `git commit` on `make fmt` and `make lint`.
#
# Receives the tool call as JSON on stdin. For Bash commands that contain a
# `git commit` invocation, it runs `make fmt` and `make lint` from the project
# root first. If either fails, the hook exits with status 2, which blocks the
# commit and feeds the failure output back to Claude. All other commands pass
# through untouched.

set -euo pipefail

command="$(jq -r '.tool_input.command // ""')"

# Only gate commands that actually invoke `git commit`.
if ! grep -qE '(^|[;&|[:space:]])git[[:space:]]+commit([[:space:]]|$)' <<<"${command}"; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:?}"

run_check() {
  local target="$1" output
  echo "pre-commit hook: running make ${target}..." >&2
  if ! output="$(make "${target}" 2>&1)"; then
    {
      echo "make ${target} failed; fix the issues before committing:"
      tail -n 50 <<<"${output}"
    } >&2
    exit 2
  fi
}

run_check fmt
run_check lint
