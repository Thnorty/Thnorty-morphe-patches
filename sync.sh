#!/usr/bin/env bash
#
# Rebuilds Thnorty patches on top of the newest official Morphe Patches release of a channel.
#
#   sync.sh stable   newest vX.Y.Z tag        -> 'main' branch, GitHub release
#   sync.sh dev      newest vX.Y.Z-dev.N tag  -> 'dev' branch, GitHub pre-release
#
# The commits of the 'features' branch (since it branched off upstream) are replayed onto the
# upstream tag, tested and built. Morphe Manager reads patches-bundle.json from 'main' (stable)
# or 'dev' (pre-release patches), which points to the release asset.
#
# Environment:
#   DRY_RUN=1          Build only. Nothing is pushed or released.
#   FEATURES_REF       Ref with the features commits (default: origin/features).
#   GITHUB_REPOSITORY  owner/repo of this fork (set by GitHub Actions).

set -euo pipefail

channel="${1:?usage: sync.sh stable|dev}"
repository="${GITHUB_REPOSITORY:-Thnorty/Thnorty-patches}"
features_ref="${FEATURES_REF:-origin/features}"
dry_run="${DRY_RUN:-0}"

case "$channel" in
  stable) tag_pattern='^v[0-9]+\.[0-9]+\.[0-9]+$'; branch=main; prerelease=0 ;;
  dev) tag_pattern='^v[0-9]+\.[0-9]+\.[0-9]+-dev\.[0-9]+$'; branch=dev; prerelease=1 ;;
  *) echo "Unknown channel: $channel" >&2; exit 2 ;;
esac

git remote get-url upstream >/dev/null 2>&1 \
  || git remote add upstream https://github.com/MorpheApp/morphe-patches.git
git fetch --quiet --tags --force upstream
if [ "$features_ref" = origin/features ]; then
  git fetch --quiet origin features
fi

upstream_tag="$(git tag --list 'v*' | grep -E "$tag_pattern" | sort -V | tail -n 1)"
if [ -z "$upstream_tag" ]; then
  echo "No upstream tag for channel $channel" >&2
  exit 1
fi

features_base="$(git merge-base "$features_ref" "$upstream_tag")"
features_sha="$(git rev-parse --short=7 "$features_ref")"
version="${upstream_tag#v}-thnorty.${features_sha}"
release_tag="v${version}"
asset_name="thnorty-patches-${version}.mpp"
echo "Channel $channel: $upstream_tag + $features_ref ($features_sha) -> $release_tag"

if [ "$dry_run" != 1 ] && gh release view "$release_tag" --repo "$repository" >/dev/null 2>&1; then
  echo "Release $release_tag already exists, nothing to do."
  exit 0
fi

# Replay the features commits onto the upstream tag. Discard leftovers of a previous run.
git reset --quiet --hard
git checkout --quiet --force --detach "$upstream_tag"
if ! git -c user.name="github-actions[bot]" \
    -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
    cherry-pick "${features_base}..${features_ref}"; then
  git cherry-pick --abort || true
  echo "::error::The features commits conflict with $upstream_tag. Rebase the features branch onto it."
  exit 1
fi

# Same order as upstream: buildAndroid adds DEX to the jar in place, so it runs last.
./gradlew --no-daemon :patches:test :extensions:reddit:testDebugUnitTest
./gradlew --no-daemon :patches:buildAndroid

mkdir -p build-output
mpp="$(ls patches/build/libs/patches-*.mpp | head -n 1)"
cp "$mpp" "build-output/$asset_name"

notes="$(printf 'Official Morphe Patches [%s](https://github.com/MorpheApp/morphe-patches/releases/tag/%s) with:\n\n%s\n' \
  "$upstream_tag" "$upstream_tag" "$(git log --reverse --format='- %s' "${features_base}..${features_ref}")")"

CREATED_AT="$(date -u +%Y-%m-%dT%H:%M:%S)" DESCRIPTION="$notes" VERSION="$version" \
DOWNLOAD_URL="https://github.com/${repository}/releases/download/${release_tag}/${asset_name}" \
"${PYTHON:-python3}" -c '
import json, os
print(json.dumps({
    "created_at": os.environ["CREATED_AT"],
    "description": os.environ["DESCRIPTION"],
    "download_url": os.environ["DOWNLOAD_URL"],
    "signature_download_url": "",
    "version": os.environ["VERSION"],
}, indent=2))' > patches-bundle.json

# Publish a single commit snapshot of the built tree. GitHub Actions tokens may not push commits
# that change workflow files, and upstream history does. Leaving out upstream's workflows also
# keeps them from ever running in this fork.
git rm -r --quiet --ignore-unmatch .github/workflows
git add patches-bundle.json
snapshot="$(git -c user.name="github-actions[bot]" \
    -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
    commit-tree "$(git write-tree)" -m "chore: Release ${version} [skip ci]

Official ${upstream_tag} with the features commits ${features_sha}.")"

if [ "$dry_run" = 1 ]; then
  echo "Dry run: built build-output/$asset_name, snapshot $snapshot"
  cat patches-bundle.json
  exit 0
fi

# The release must exist before Manager can read the bundle file that points to it.
git push --quiet --force origin "${snapshot}:refs/heads/build-${channel}"
release_args=(--repo "$repository" --target "build-${channel}" --title "Thnorty patches ${version}" --notes "$notes")
if [ "$prerelease" = 1 ]; then
  release_args+=(--prerelease)
else
  release_args+=(--latest)
fi
gh release create "$release_tag" "build-output/$asset_name" "${release_args[@]}"
git push --quiet --force origin "${snapshot}:refs/heads/${branch}"
echo "Published $release_tag to the $branch branch."
