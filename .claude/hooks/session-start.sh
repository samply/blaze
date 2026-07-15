#!/bin/bash
# SessionStart hook: install the Java + Clojure toolchain for Claude Code on the web.
#
# Blaze is a Clojure project built with tools.deps. The base remote image ships
# Java 21 and GNU Make, but not Java 25, the Clojure CLI, clj-kondo, cljfmt,
# actionlint, or ShellCheck, which the Makefile targets (make fmt / make lint /
# make test) depend on. This installs them, pinning the tool versions used by
# CI (.github/workflows/build.yml and .github/scripts/install-actionlint.sh).
#
# In the remote sandbox all github.com traffic goes through a GitHub proxy that
# is scoped to the repositories granted to the session, so GitHub release
# assets of other repositories are not downloadable (403 before the redirect to
# the signed release-assets.githubusercontent.com URL is ever issued). Hosts
# other than github.com / api.github.com / codeload.github.com are not scoped
# by that proxy, so every tool is installed from such a source:
#
# * Eclipse Temurin JDK: Adoptium apt repository (packages.adoptium.net)
# * Clojure CLI: official tools tarball from download.clojure.org
# * clj-kondo: native binary extracted from the official Docker image on
#   Docker Hub (registry-1.docker.io)
# * cljfmt: native binary from the Homebrew bottle on ghcr.io
# * actionlint: native binary from the Homebrew bottle on ghcr.io
# * rlwrap, ShellCheck: Ubuntu apt archive
#
# Each tool is installed under its own guard: a failing install degrades to a
# warning instead of aborting the remaining installations.
#
# Synchronous and idempotent: each tool is only fetched if it is missing.

set -euo pipefail

# Only run in the remote (Claude Code on the web) environment.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# Java feature version to install (Eclipse Temurin, latest GA patch of this line).
# renovate: datasource=docker depName=eclipse-temurin versioning=docker
JAVA_VERSION="25"

# Versions pinned to match CI (.github/workflows/build.yml).
# renovate: datasource=github-releases depName=clojure/brew-install versioning=loose
CLOJURE_CLI_VERSION="1.12.5.1654"
# renovate: datasource=github-releases depName=clj-kondo/clj-kondo versioning=loose extractVersion=^v(?<version>.+)$
CLJ_KONDO_VERSION="2026.05.25"
# renovate: datasource=github-releases depName=weavejester/cljfmt
CLJFMT_VERSION="0.16.5"
# Pinned to match .github/scripts/install-actionlint.sh.
# renovate: datasource=github-releases depName=rhysd/actionlint extractVersion=^v(?<version>.+)$
ACTIONLINT_VERSION="1.7.12"

BIN_DIR="/usr/local/bin"
LIB_DIR="/usr/local/lib"
MAN_DIR="/usr/local/share/man/man1"

warn() {
  echo "warning: $*" >&2
}

# The installer functions below are called from contexts where `set -e` has no
# effect (the left-hand side of `||`), so they chain their commands with `&&`
# explicitly and report failure via their exit status.

find_java_home() {
  find /usr/lib/jvm -maxdepth 1 -type d -name "temurin-${JAVA_VERSION}-jdk*" 2>/dev/null | sort | tail -1
}

# --allow-releaseinfo-change: pre-existing third-party repositories in the
# base image occasionally change their release info (e.g. the ondrej/php PPA
# changed its Label), which apt otherwise treats as a hard error. An update
# failure of an unrelated repository must not block the install either, so it
# is downgraded to a warning and the install alone decides the exit status.
install_apt_tools() {
  apt-get update -qq --allow-releaseinfo-change ||
    warn "apt-get update failed for some repositories; trying to install anyway"
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "$@"
}

install_java() {
  mkdir -p /etc/apt/keyrings &&
    curl -sfL -o /etc/apt/keyrings/adoptium.asc \
      "https://packages.adoptium.net/artifactory/api/gpg/key/public" &&
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "${VERSION_CODENAME}") main" \
      >/etc/apt/sources.list.d/adoptium.list &&
    install_apt_tools "temurin-${JAVA_VERSION}-jdk"
}

install_clojure_cli() {
  local tmp install_dir status
  install_dir="${LIB_DIR}/clojure"
  tmp="$(mktemp -d)" || return 1
  curl -sfL -o "${tmp}/clojure-tools.tar.gz" \
    "https://download.clojure.org/install/clojure-tools-${CLOJURE_CLI_VERSION}.tar.gz" &&
    tar -xzf "${tmp}/clojure-tools.tar.gz" -C "${tmp}" &&
    mkdir -p "${install_dir}/libexec" "${BIN_DIR}" "${MAN_DIR}" &&
    install -m 0644 "${tmp}/clojure-tools/deps.edn" "${tmp}/clojure-tools/example-deps.edn" \
      "${tmp}/clojure-tools/tools.edn" "${install_dir}" &&
    install -m 0644 "${tmp}/clojure-tools"/*.jar "${install_dir}/libexec" &&
    sed -e "s@PREFIX@${install_dir}@g" "${tmp}/clojure-tools/clojure" >"${BIN_DIR}/clojure" &&
    sed -e "s@BINDIR@${BIN_DIR}@g" "${tmp}/clojure-tools/clj" >"${BIN_DIR}/clj" &&
    chmod 0755 "${BIN_DIR}/clojure" "${BIN_DIR}/clj" &&
    install -m 0644 "${tmp}/clojure-tools/clojure.1" "${tmp}/clojure-tools/clj.1" "${MAN_DIR}"
  status=$?
  rm -rf "${tmp}"
  return ${status}
}

# Extracts the clj-kondo binary from the linux/amd64 variant of the official
# cljkondo/clj-kondo Docker image, pulled directly via the registry API.
install_clj_kondo() {
  local tmp token digest layers layer status=1
  tmp="$(mktemp -d)" || return 1
  if token="$(curl -sf "https://auth.docker.io/token?service=registry.docker.io&scope=repository:cljkondo/clj-kondo:pull" |
    jq -re .token)" &&
    digest="$(curl -sf -H "Authorization: Bearer ${token}" \
      -H "Accept: application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.index.v1+json" \
      "https://registry-1.docker.io/v2/cljkondo/clj-kondo/manifests/${CLJ_KONDO_VERSION}" |
      jq -re 'first(.manifests[] | select(.platform.architecture == "amd64" and .platform.os == "linux")).digest')" &&
    layers="$(curl -sf -H "Authorization: Bearer ${token}" \
      -H "Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json" \
      "https://registry-1.docker.io/v2/cljkondo/clj-kondo/manifests/${digest}" |
      jq -re '.layers[].digest')"; then
    # The binary sits in one of the image layers, most likely the last one.
    for layer in $(printf '%s\n' "${layers}" | tac); do
      if curl -sfL -H "Authorization: Bearer ${token}" -o "${tmp}/layer.tar.gz" \
        "https://registry-1.docker.io/v2/cljkondo/clj-kondo/blobs/${layer}" &&
        tar -xzf "${tmp}/layer.tar.gz" -C "${tmp}" usr/bin/clj-kondo 2>/dev/null; then
        break
      fi
    done
  fi
  if [ -f "${tmp}/usr/bin/clj-kondo" ]; then
    install -m 0755 "${tmp}/usr/bin/clj-kondo" "${BIN_DIR}/clj-kondo"
    status=$?
  fi
  rm -rf "${tmp}"
  return ${status}
}

# Downloads the x86_64_linux Homebrew bottle of a homebrew-core formula from
# ghcr.io (anonymous pull).
fetch_bottle() {
  local formula="$1" version="$2" dest="$3"
  local token digest
  token="$(curl -sf "https://ghcr.io/token?service=ghcr.io&scope=repository:homebrew/core/${formula}:pull" |
    jq -re .token)" &&
    digest="$(curl -sf -H "Authorization: Bearer ${token}" \
      -H "Accept: application/vnd.oci.image.index.v1+json" \
      "https://ghcr.io/v2/homebrew/core/${formula}/manifests/${version}" |
      jq -re 'first(.manifests[] | select(.annotations."org.opencontainers.image.ref.name" // "" | endswith("x86_64_linux"))).annotations."sh.brew.bottle.digest"')" &&
    curl -sfL -H "Authorization: Bearer ${token}" -o "${dest}" \
      "https://ghcr.io/v2/homebrew/core/${formula}/blobs/sha256:${digest}"
}

# The cljfmt bottle is a Linuxbrew binary whose ELF interpreter is the literal
# placeholder @@HOMEBREW_PREFIX@@/lib/ld.so (normally rewritten by `brew`), so
# it is run through the system dynamic loader via a wrapper script.
install_cljfmt() {
  local tmp status
  tmp="$(mktemp -d)" || return 1
  fetch_bottle cljfmt "${CLJFMT_VERSION}" "${tmp}/bottle.tar.gz" &&
    tar -xzf "${tmp}/bottle.tar.gz" -C "${tmp}" "cljfmt/${CLJFMT_VERSION}/bin/cljfmt" &&
    mkdir -p "${LIB_DIR}/cljfmt" &&
    install -m 0755 "${tmp}/cljfmt/${CLJFMT_VERSION}/bin/cljfmt" "${LIB_DIR}/cljfmt/cljfmt" &&
    printf '#!/bin/bash\nexec /lib64/ld-linux-x86-64.so.2 %s/cljfmt/cljfmt "$@"\n' "${LIB_DIR}" \
      >"${BIN_DIR}/cljfmt" &&
    chmod 0755 "${BIN_DIR}/cljfmt"
  status=$?
  rm -rf "${tmp}"
  return ${status}
}

# The actionlint bottle is a statically linked Go binary and runs as is.
install_actionlint() {
  local tmp status
  tmp="$(mktemp -d)" || return 1
  fetch_bottle actionlint "${ACTIONLINT_VERSION}" "${tmp}/bottle.tar.gz" &&
    tar -xzf "${tmp}/bottle.tar.gz" -C "${tmp}" "actionlint/${ACTIONLINT_VERSION}/bin/actionlint" &&
    install -m 0755 "${tmp}/actionlint/${ACTIONLINT_VERSION}/bin/actionlint" "${BIN_DIR}/actionlint"
  status=$?
  rm -rf "${tmp}"
  return ${status}
}

# Eclipse Temurin JDK. Installed alongside the base JDK and made the active
# one for the session via JAVA_HOME / PATH written to $CLAUDE_ENV_FILE.
java_home="$(find_java_home)"
if [ -z "${java_home}" ]; then
  echo "Installing Eclipse Temurin JDK ${JAVA_VERSION} from packages.adoptium.net..."
  if install_java; then
    java_home="$(find_java_home)"
  else
    warn "could not install Eclipse Temurin JDK ${JAVA_VERSION}; staying on the base image Java"
  fi
else
  echo "Java ${JAVA_VERSION} already installed: ${java_home}"
fi

# Activate the Temurin JDK for this session (and child processes like
# make/clojure).
if [ -n "${java_home}" ]; then
  export JAVA_HOME="${java_home}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
  if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    echo "export JAVA_HOME=\"${JAVA_HOME}\"" >>"${CLAUDE_ENV_FILE}"
    echo "export PATH=\"${JAVA_HOME}/bin:\${PATH}\"" >>"${CLAUDE_ENV_FILE}"
  fi
fi

# rlwrap is used by the `clj` REPL wrapper and ShellCheck by `make lint` via
# the lint-shell target.
apt_tools=()
command -v rlwrap >/dev/null 2>&1 || apt_tools+=(rlwrap)
command -v shellcheck >/dev/null 2>&1 || apt_tools+=(shellcheck)
if [ "${#apt_tools[@]}" -gt 0 ]; then
  echo "Installing ${apt_tools[*]}..."
  install_apt_tools "${apt_tools[@]}" ||
    warn "could not install: ${apt_tools[*]}"
fi

# Clojure CLI (provides `clojure` and `clj`).
if ! command -v clojure >/dev/null 2>&1; then
  echo "Installing Clojure CLI ${CLOJURE_CLI_VERSION} from download.clojure.org..."
  install_clojure_cli ||
    warn "could not install the Clojure CLI (make prep / make test unavailable)"
else
  echo "Clojure CLI already installed: $(clojure --version 2>&1)"
fi

# clj-kondo (used by `make lint`).
if ! command -v clj-kondo >/dev/null 2>&1; then
  echo "Installing clj-kondo ${CLJ_KONDO_VERSION} from Docker Hub..."
  install_clj_kondo ||
    warn "could not install clj-kondo (make lint unavailable)"
else
  echo "clj-kondo already installed: $(clj-kondo --version 2>&1)"
fi

# cljfmt (used by `make fmt`).
if ! command -v cljfmt >/dev/null 2>&1; then
  echo "Installing cljfmt ${CLJFMT_VERSION} from ghcr.io..."
  install_cljfmt ||
    warn "could not install cljfmt (make fmt unavailable)"
else
  echo "cljfmt already installed: $(cljfmt --version 2>&1)"
fi

# actionlint (used by `make lint` via the lint-workflows target).
if ! command -v actionlint >/dev/null 2>&1; then
  echo "Installing actionlint ${ACTIONLINT_VERSION} from ghcr.io..."
  install_actionlint ||
    warn "could not install actionlint (make lint-workflows unavailable)"
else
  echo "actionlint already installed: $(actionlint --version 2>&1 | head -1)"
fi

echo "Java + Clojure toolchain ready (JAVA_HOME=${JAVA_HOME:-<unset>})."
