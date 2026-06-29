#!/bin/bash
# SessionStart hook: install the Java + Clojure toolchain for Claude Code on the web.
#
# Blaze is a Clojure project built with tools.deps. The base remote image ships
# Java 21 and GNU Make, but not Java 25, the Clojure CLI, clj-kondo, or cljfmt,
# which the Makefile targets (make fmt / make lint / make test) depend on. This
# installs them, pinning the Clojure tool versions used by CI
# (.github/workflows/build.yml).
#
# Synchronous and idempotent: each tool is only fetched if it is missing.

set -euo pipefail

# Only run in the remote (Claude Code on the web) environment.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# Java feature version to install (Eclipse Temurin, latest GA patch of this line).
JAVA_VERSION="25"
JAVA_INSTALL_DIR="/opt/java"

# Versions pinned to match CI (.github/workflows/build.yml).
CLOJURE_CLI_VERSION="1.12.4.1629"
CLJ_KONDO_VERSION="2026.01.19"
CLJFMT_VERSION="0.16.3"

BIN_DIR="/usr/local/bin"

# Eclipse Temurin JDK 25. Installed alongside the base JDK and made the active
# one for the session via JAVA_HOME / PATH written to $CLAUDE_ENV_FILE.
java_home=""
if [ -d "${JAVA_INSTALL_DIR}" ]; then
  java_home="$(find "${JAVA_INSTALL_DIR}" -maxdepth 1 -type d -name "jdk-${JAVA_VERSION}*" | sort | tail -1)"
fi
if [ -z "${java_home}" ]; then
  echo "Installing Eclipse Temurin JDK ${JAVA_VERSION}..."
  tmp="$(mktemp -d)"
  curl -sL -o "${tmp}/temurin.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/${JAVA_VERSION}/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p "${JAVA_INSTALL_DIR}"
  tar -xzf "${tmp}/temurin.tar.gz" -C "${JAVA_INSTALL_DIR}"
  rm -rf "${tmp}"
  java_home="$(find "${JAVA_INSTALL_DIR}" -maxdepth 1 -type d -name "jdk-${JAVA_VERSION}*" | sort | tail -1)"
else
  echo "Java ${JAVA_VERSION} already installed: ${java_home}"
fi

# Activate JDK 25 for this session (and child processes like make/clojure).
export JAVA_HOME="${java_home}"
export PATH="${JAVA_HOME}/bin:${PATH}"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export JAVA_HOME=\"${JAVA_HOME}\"" >> "${CLAUDE_ENV_FILE}"
  echo "export PATH=\"${JAVA_HOME}/bin:\${PATH}\"" >> "${CLAUDE_ENV_FILE}"
fi

# rlwrap is used by the `clj` REPL wrapper; harmless if it cannot be installed.
if ! command -v rlwrap >/dev/null 2>&1; then
  echo "Installing rlwrap..."
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update -qq && apt-get install -y -qq rlwrap || \
      echo "warning: could not install rlwrap (clj REPL line editing unavailable)"
  fi
fi

# Clojure CLI (provides `clojure` and `clj`).
if ! command -v clojure >/dev/null 2>&1; then
  echo "Installing Clojure CLI ${CLOJURE_CLI_VERSION}..."
  tmp="$(mktemp -d)"
  curl -sL -o "${tmp}/linux-install.sh" \
    "https://github.com/clojure/brew-install/releases/download/${CLOJURE_CLI_VERSION}/linux-install.sh"
  bash "${tmp}/linux-install.sh"
  rm -rf "${tmp}"
else
  echo "Clojure CLI already installed: $(clojure --version 2>&1)"
fi

# clj-kondo (used by `make lint`).
if ! command -v clj-kondo >/dev/null 2>&1; then
  echo "Installing clj-kondo ${CLJ_KONDO_VERSION}..."
  tmp="$(mktemp -d)"
  curl -sL -o "${tmp}/install-clj-kondo" \
    "https://raw.githubusercontent.com/clj-kondo/clj-kondo/master/script/install-clj-kondo"
  chmod +x "${tmp}/install-clj-kondo"
  "${tmp}/install-clj-kondo" --dir "${BIN_DIR}" --version "${CLJ_KONDO_VERSION}"
  rm -rf "${tmp}"
else
  echo "clj-kondo already installed: $(clj-kondo --version 2>&1)"
fi

# cljfmt (used by `make fmt`).
if ! command -v cljfmt >/dev/null 2>&1; then
  echo "Installing cljfmt ${CLJFMT_VERSION}..."
  tmp="$(mktemp -d)"
  curl -sL -o "${tmp}/cljfmt.tar.gz" \
    "https://github.com/weavejester/cljfmt/releases/download/${CLJFMT_VERSION}/cljfmt-${CLJFMT_VERSION}-linux-amd64-static.tar.gz"
  tar -xzf "${tmp}/cljfmt.tar.gz" -C "${tmp}"
  install -m 0755 "${tmp}/cljfmt" "${BIN_DIR}/cljfmt"
  rm -rf "${tmp}"
else
  echo "cljfmt already installed: $(cljfmt --version 2>&1)"
fi

echo "Java + Clojure toolchain ready (JAVA_HOME=${JAVA_HOME})."
