#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${VERSION:?Provide an immutable release tag}"
[[ "$VERSION" =~ ^[0-9][0-9A-Za-z._-]*$ ]] || { echo 'Invalid release tag' >&2; exit 1; }
staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT
# 从不可变版本 Tag 取得提交和 tree，沿用 JitPack 的回执与归档校验。
repo_root="$PWD"
git init -q "$staging/tag"
git -C "$staging/tag" fetch -q --no-tags --depth=1 https://github.com/gycrosskit/permission.git "refs/tags/$VERSION"
git -C "$staging/tag" checkout -q --detach FETCH_HEAD
commit="$(git -C "$staging/tag" rev-parse --verify HEAD)"
(cd "$staging/tag" && bash "$repo_root/jitpack-install.sh" permission "$staging/maven")
python3 scripts/check-maven.py "$staging/maven" com.github.gycrosskit.permission "$VERSION" permission-core,permission-kuikly ios_arm64,ios_x64,ios_simulator_arm64,ohos_arm64
# 标签必须解析为不可变发布提交；不把当前 PR 的 SHA 当成已发布版本。
publications=permission-core,permission-core-android,permission-core-iosarm64,permission-core-iosx64,permission-core-iossimulatorarm64,permission-core-ohosarm64,permission-kuikly,permission-kuikly-ohosarm64
case "$VERSION" in
  0.1.[0-8]) ;;
  *) publications=permission-core,permission-core-android,permission-core-iosarm64,permission-core-iosx64,permission-core-iossimulatorarm64,permission-core-ohosarm64,permission-kuikly,permission-kuikly-android,permission-kuikly-iosarm64,permission-kuikly-iosx64,permission-kuikly-iossimulatorarm64,permission-kuikly-ohosarm64 ;;
esac
python3 scripts/check-public-maven.py --repo permission --version "$VERSION" --commit "$commit" \
  --expected-publications "$publications" --output-dir "${CI_DIAGNOSTICS_DIR:-ci-diagnostics}/public"
