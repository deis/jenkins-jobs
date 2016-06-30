#!/usr/bin/env bash

set -eo pipefail

main() {
  check-release-branch
}

check-release-branch() {
  if [ "${GIT_BRANCH}" != "origin/release-${RELEASE}" ]; then
    echo "GIT_BRANCH (${GIT_BRANCH}) is not 'origin/release-${RELEASE}'; exiting build."
    exit 0
  fi
  echo "Proceeding with build/deploy from 'origin/release-${RELEASE}'..."
}
