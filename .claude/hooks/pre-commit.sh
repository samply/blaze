#!/bin/bash
# PreToolUse hook: gate `git commit` on `fmt` and `lint` of the changed parts.
#
# Receives the tool call as JSON on stdin. For Bash commands that contain a
# `git commit` invocation, it runs the `fmt` and `lint` checks first, but only
# for the parts of the project that changed:
#
# * each changed module under `modules/` runs `make -C modules/<m> fmt` and
#   `make -C modules/<m> lint`, skipping targets its Makefile doesn't have
# * changed shell scripts inside modules run `make lint-shell`, because only
#   the root lints shell scripts
# * changes outside `modules/` run `make fmt-root` and `make lint-root`
# * changes to configuration shared by all modules run the full `make fmt` and
#   `make lint`
#
# If nothing needs checking, the checks are skipped. If a check fails, the hook
# exits with status 2, which blocks the commit and feeds the failure output
# back to Claude. All other commands pass through untouched.
#
# The hook runs before the Bash command, so files staged by the same command
# (`git add ... && git commit`, `git commit -a`) aren't staged yet. Because of
# that, all files differing from HEAD, including unstaged and untracked ones,
# are considered. That's a superset of what gets committed.
#
# Limitation: clj-kondo checks calls across namespaces using its cache, so a
# change in one module can break the lint of a dependent module that isn't
# checked here. CI still runs the full lint.

set -euo pipefail

command="$(jq -r '.tool_input.command // ""')"

# Only gate commands that actually invoke `git commit`.
if ! grep -qE '(^|[;&|[:space:]])git[[:space:]]+commit([[:space:]]|$)' <<<"${command}"; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:?}"

run_check() {
  local output
  echo "pre-commit hook: running make $*..." >&2
  if ! output="$(make "$@" 2>&1)"; then
    {
      echo "make $* failed; fix the issues before committing:"
      tail -n 50 <<<"${output}"
    } >&2
    exit 2
  fi
}

# Prints all files differing from HEAD, including unstaged and untracked ones,
# NUL-separated. Deleted and renamed files are printed with their old path.
changed_files() {
  git diff --name-only --no-renames -z HEAD
  git ls-files --others --exclude-standard -z
}

# Returns success if the Makefile of the module has the target.
has_target() {
  local module="$1" target="$2"
  grep -qE "^${target}:" "${module}/Makefile" 2>/dev/null
}

# Without HEAD (no commit yet) there is nothing to diff against.
if ! git rev-parse --verify --quiet HEAD >/dev/null; then
  run_check fmt
  run_check lint
  exit 0
fi

full=false
root=false
shell=false
modules=""

while IFS= read -r -d '' path; do
  case "${path}" in
    Makefile | cljfmt.edn | .clj-kondo/* | modules/*/resources/clj-kondo.exports/*)
      full=true
      ;;
    modules/*/*)
      module="${path#modules/}"
      module="modules/${module%%/*}"
      if [[ -d "${module}" ]]; then
        modules+="${module}"$'\n'
        if [[ "${path}" == *.sh ]]; then
          shell=true
        fi
      fi
      ;;
    *)
      root=true
      ;;
  esac
done < <(changed_files)

if [[ "${full}" == true ]]; then
  run_check fmt
  run_check lint
  exit 0
fi

# Module paths contain no whitespace, so they can be word-split below.
modules="$(printf '%s' "${modules}" | sort -u)"

if [[ -z "${modules}" && "${root}" == false ]]; then
  echo "pre-commit hook: nothing to check" >&2
  exit 0
fi

for module in ${modules}; do
  if has_target "${module}" fmt; then
    run_check -C "${module}" fmt
  fi
done
if [[ "${root}" == true ]]; then
  run_check fmt-root
fi

for module in ${modules}; do
  if has_target "${module}" lint; then
    run_check -C "${module}" lint
  fi
done
if [[ "${root}" == true ]]; then
  run_check lint-root
elif [[ "${shell}" == true ]]; then
  run_check lint-shell
fi
