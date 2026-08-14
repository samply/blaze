# Dev container image for Blaze development.
#
# Based on Ubuntu 24.04 (Noble), the distribution the CI pipeline runs on. The
# toolchain itself is installed by install-tools.sh.
#
# This image contains no Docker. Building the Blaze image (`make build-image`)
# and running the docker compose based integration tests under .github/ is done
# on the host.
FROM mcr.microsoft.com/devcontainers/base:noble@sha256:cfd5dd36c0d0d88de015aad5aa4f4b94e76d9f658a1e3055b27fd7d0fd1d84a6

COPY install-tools.sh /tmp/install-tools.sh
RUN /tmp/install-tools.sh && rm /tmp/install-tools.sh

# Dependency caches and the npm prefix for globally installed packages like the
# Claude Code CLI. Created up front, so that the volumes the dev container
# mounts on top of them inherit their ownership.
RUN install -d -o vscode -g vscode \
    /home/vscode/.m2 \
    /home/vscode/.gitlibs \
    /home/vscode/.npm-global \
    /home/vscode/.claude

ENV JAVA_HOME="/usr/lib/jvm/temurin-jdk"
ENV NPM_CONFIG_PREFIX="/home/vscode/.npm-global"
ENV PATH="${JAVA_HOME}/bin:${NPM_CONFIG_PREFIX}/bin:${PATH}"
