#!/bin/bash
# Installs the Blaze toolchain into the dev container image.
#
# Run as root from the Dockerfile. The versions match the ones used by the CI
# pipeline (.github/workflows/build.yml), so that `make fmt`, `make lint` and
# `make test` behave inside the dev container like they do in CI.
#
# Every artifact is downloaded from its upstream source and verified against a
# SHA-256 checksum. Because the binaries are architecture specific, both the
# amd64 and the arm64 checksum have to be updated whenever a version changes.
# Renovate only bumps the version (see renovate.json).

set -euo pipefail

# Eclipse Temurin JDK, installed from the Adoptium apt repository. Blaze needs
# at least Java 21; CI tests with 21 and 25.
# renovate: datasource=docker depName=eclipse-temurin versioning=docker
JAVA_VERSION="25"

# renovate: datasource=github-releases depName=clojure/brew-install versioning=loose
CLOJURE_CLI_VERSION="1.12.5.1664"
CLOJURE_CLI_CHECKSUM="77dd6868948074adcc93e83a796f8e8f15a1a92bcb1b9002d715fd2210e476f3"

# renovate: datasource=github-releases depName=clj-kondo/clj-kondo versioning=loose extractVersion=^v(?<version>.+)$
CLJ_KONDO_VERSION="2026.08.04"
CLJ_KONDO_CHECKSUM_AMD64="25dce55f597cc7f034f6c5f8bf4195128e3efeb3b22710a066fb67b489e70a3b"
CLJ_KONDO_CHECKSUM_ARM64="9ef3add2c69a24cc5f40b07829f23407b36bba021c4345f4587fb6d51533ee25"

# renovate: datasource=github-releases depName=weavejester/cljfmt
CLJFMT_VERSION="0.16.5"
CLJFMT_CHECKSUM_AMD64="69b7961d8fc5636ecbff932c557a7670316183afa489b2c88b2cff5958b8696f"
CLJFMT_CHECKSUM_ARM64="39f3d742d8675bda829405c0c00afaf9463a7754cf86c5de27aaf6683125e629"

# renovate: datasource=github-releases depName=rhysd/actionlint extractVersion=^v(?<version>.+)$
ACTIONLINT_VERSION="1.7.12"
ACTIONLINT_CHECKSUM_AMD64="8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8"
ACTIONLINT_CHECKSUM_ARM64="325e971b6ba9bfa504672e29be93c24981eeb1c07576d730e9f7c8805afff0c6"

# renovate: datasource=github-releases depName=grafana/dashboard-linter extractVersion=^v(?<version>.+)$
DASHBOARD_LINTER_VERSION="0.2.0"
DASHBOARD_LINTER_CHECKSUM_AMD64="6cf73633018ef705cd12eda88736b872f5858137baa9dc9df83262d721934fac"
DASHBOARD_LINTER_CHECKSUM_ARM64="3cc1022ceafd136e5ee6c2caa0ba6d01e3e2dea1c5eedefd012f0ae14f42defe"

# Keep in sync with .nvmrc. Node.js publishes a checksum file per release, so
# no checksum has to be pinned here.
# renovate: datasource=node-version depName=node
NODE_VERSION="24.19.0"

BIN_DIR="/usr/local/bin"
LIB_DIR="/usr/local/lib"
MAN_DIR="/usr/local/share/man/man1"

ARCH="$(dpkg --print-architecture)"
case "${ARCH}" in
amd64 | arm64) ;;
*)
  echo "unsupported architecture: ${ARCH}" >&2
  exit 1
  ;;
esac

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

log() {
  echo "==> $*"
}

# Echoes the first argument on amd64 and the second one on arm64.
by_arch() {
  if [ "${ARCH}" = "amd64" ]; then echo "$1"; else echo "$2"; fi
}

# Downloads the file at the URL $1 to $2 and verifies it against the SHA-256
# checksum $3.
download() {
  curl -sSfL -o "$2" "$1"
  echo "$3  $2" | sha256sum --check --quiet -
}

# Eclipse Temurin JDK and the packages the Makefile targets rely on: GNU Make,
# jq and unzip for the tooling, rlwrap for the `clj` REPL wrapper, ShellCheck
# for `make lint` (lint-shell target) and xz-utils to unpack Node.js.
install_apt_packages() {
  log "Installing the Eclipse Temurin JDK ${JAVA_VERSION} and base packages"
  mkdir -p /etc/apt/keyrings
  curl -sSfL -o /etc/apt/keyrings/adoptium.asc \
    "https://packages.adoptium.net/artifactory/api/gpg/key/public"
  echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "${VERSION_CODENAME}") main" \
    >/etc/apt/sources.list.d/adoptium.list
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends \
    "temurin-${JAVA_VERSION}-jdk" jq make rlwrap shellcheck unzip xz-utils
  rm -rf /var/lib/apt/lists/*

  # Architecture independent path to the JDK, used as JAVA_HOME.
  ln -sfn "/usr/lib/jvm/temurin-${JAVA_VERSION}-jdk-${ARCH}" /usr/lib/jvm/temurin-jdk
}

# Clojure CLI, installed from the official tools tarball the same way the
# linux-install script does.
install_clojure_cli() {
  log "Installing the Clojure CLI ${CLOJURE_CLI_VERSION}"
  local dir="${LIB_DIR}/clojure"
  download "https://download.clojure.org/install/clojure-tools-${CLOJURE_CLI_VERSION}.tar.gz" \
    "${TMP_DIR}/clojure-tools.tar.gz" "${CLOJURE_CLI_CHECKSUM}"
  tar -xzf "${TMP_DIR}/clojure-tools.tar.gz" -C "${TMP_DIR}"
  mkdir -p "${dir}/libexec" "${MAN_DIR}"
  install -m 0644 "${TMP_DIR}/clojure-tools/deps.edn" \
    "${TMP_DIR}/clojure-tools/example-deps.edn" \
    "${TMP_DIR}/clojure-tools/tools.edn" "${dir}"
  install -m 0644 "${TMP_DIR}/clojure-tools"/*.jar "${dir}/libexec"
  sed -e "s@PREFIX@${dir}@g" "${TMP_DIR}/clojure-tools/clojure" >"${BIN_DIR}/clojure"
  sed -e "s@BINDIR@${BIN_DIR}@g" "${TMP_DIR}/clojure-tools/clj" >"${BIN_DIR}/clj"
  chmod 0755 "${BIN_DIR}/clojure" "${BIN_DIR}/clj"
  install -m 0644 "${TMP_DIR}/clojure-tools/clojure.1" \
    "${TMP_DIR}/clojure-tools/clj.1" "${MAN_DIR}"
}

# clj-kondo, used by `make lint`.
install_clj_kondo() {
  log "Installing clj-kondo ${CLJ_KONDO_VERSION}"
  local file
  file="clj-kondo-${CLJ_KONDO_VERSION}-$(by_arch "linux-static-amd64" "linux-aarch64").zip"
  download "https://github.com/clj-kondo/clj-kondo/releases/download/v${CLJ_KONDO_VERSION}/${file}" \
    "${TMP_DIR}/clj-kondo.zip" \
    "$(by_arch "${CLJ_KONDO_CHECKSUM_AMD64}" "${CLJ_KONDO_CHECKSUM_ARM64}")"
  unzip -q -o -d "${TMP_DIR}" "${TMP_DIR}/clj-kondo.zip" clj-kondo
  install -m 0755 "${TMP_DIR}/clj-kondo" "${BIN_DIR}/clj-kondo"
}

# cljfmt, used by `make fmt`.
install_cljfmt() {
  log "Installing cljfmt ${CLJFMT_VERSION}"
  local file
  file="cljfmt-${CLJFMT_VERSION}-linux-$(by_arch "amd64" "aarch64").tar.gz"
  download "https://github.com/weavejester/cljfmt/releases/download/${CLJFMT_VERSION}/${file}" \
    "${TMP_DIR}/cljfmt.tar.gz" \
    "$(by_arch "${CLJFMT_CHECKSUM_AMD64}" "${CLJFMT_CHECKSUM_ARM64}")"
  tar -xzf "${TMP_DIR}/cljfmt.tar.gz" -C "${TMP_DIR}" cljfmt
  install -m 0755 "${TMP_DIR}/cljfmt" "${BIN_DIR}/cljfmt"
}

# actionlint, used by `make lint` (lint-workflows target).
install_actionlint() {
  log "Installing actionlint ${ACTIONLINT_VERSION}"
  local file
  file="actionlint_${ACTIONLINT_VERSION}_linux_${ARCH}.tar.gz"
  download "https://github.com/rhysd/actionlint/releases/download/v${ACTIONLINT_VERSION}/${file}" \
    "${TMP_DIR}/actionlint.tar.gz" \
    "$(by_arch "${ACTIONLINT_CHECKSUM_AMD64}" "${ACTIONLINT_CHECKSUM_ARM64}")"
  tar -xzf "${TMP_DIR}/actionlint.tar.gz" -C "${TMP_DIR}" actionlint
  install -m 0755 "${TMP_DIR}/actionlint" "${BIN_DIR}/actionlint"
}

# dashboard-linter, used by `make -C modules/monitoring lint-dashboard`.
install_dashboard_linter() {
  log "Installing dashboard-linter ${DASHBOARD_LINTER_VERSION}"
  local file
  file="dashboard-linter_${DASHBOARD_LINTER_VERSION}_linux_${ARCH}.tar.gz"
  download "https://github.com/grafana/dashboard-linter/releases/download/v${DASHBOARD_LINTER_VERSION}/${file}" \
    "${TMP_DIR}/dashboard-linter.tar.gz" \
    "$(by_arch "${DASHBOARD_LINTER_CHECKSUM_AMD64}" "${DASHBOARD_LINTER_CHECKSUM_ARM64}")"
  tar -xzf "${TMP_DIR}/dashboard-linter.tar.gz" -C "${TMP_DIR}" dashboard-linter
  install -m 0755 "${TMP_DIR}/dashboard-linter" "${BIN_DIR}/dashboard-linter"
}

# Node.js, needed by the frontend module and by SUSHI (`make build-ig`).
install_node() {
  log "Installing Node.js ${NODE_VERSION}"
  local file
  file="node-v${NODE_VERSION}-linux-$(by_arch "x64" "arm64").tar.xz"
  curl -sSfL -o "${TMP_DIR}/${file}" "https://nodejs.org/dist/v${NODE_VERSION}/${file}"
  curl -sSfL -o "${TMP_DIR}/SHASUMS256.txt" "https://nodejs.org/dist/v${NODE_VERSION}/SHASUMS256.txt"
  (cd "${TMP_DIR}" && grep " ${file}\$" SHASUMS256.txt | sha256sum --check --quiet -)
  tar -xJf "${TMP_DIR}/${file}" -C /usr/local --strip-components=1 \
    --exclude=CHANGELOG.md --exclude=LICENSE --exclude=README.md
}

install_apt_packages
install_clojure_cli
install_clj_kondo
install_cljfmt
install_actionlint
install_dashboard_linter
install_node

log "Toolchain installed"
