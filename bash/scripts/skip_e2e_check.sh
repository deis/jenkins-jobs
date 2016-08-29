#!/usr/bin/env bash
set -eo pipefail

# check-skip-e2e checks if 'skip e2e' is provided in commit body
check-skip-e2e() {
  git_commit="${1}"
  commit_description="${COMMIT_DESCRIPTION:-$(git log --format=%B -n 1 "${git_commit}")}"

  skipE2e=$(echo "${commit_description}" | grep -o "skip e2e") || true

  if [ -n "${skipE2e}" ]; then
    echo "SKIP_E2E=true"
  fi
}
