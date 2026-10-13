#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

image="$1"
title="$2"
description="$3"
version="$4"
revision="$5"

configs="$(docker buildx imagetools inspect "$image" --format '{{json .Image}}')"

label() {
  echo "$configs" | jq -r --arg platform "$platform" --arg name "org.opencontainers.image.$1" '.[$platform].config.Labels[$name] // empty'
}

for platform in linux/amd64 linux/arm64; do
  test "$platform title label" "$(label title)" "$title"
  test "$platform description label" "$(label description)" "$description"
  test "$platform url label" "$(label url)" "https://blaze-server.org"
  test "$platform documentation label" "$(label documentation)" "https://blaze-server.org"
  test "$platform source label" "$(label source)" "https://github.com/samply/blaze"
  test "$platform licenses label" "$(label licenses)" "Apache-2.0"
  test_non_empty "$platform version label" "$(label version)"
  test "$platform version label" "$(label version)" "$version"
  test "$platform revision label" "$(label revision)" "$revision"
  test_empty "$platform created label" "$(label created)"
done
