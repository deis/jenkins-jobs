#!/usr/bin/env bash

set -eo pipefail

get-latest-tag() {
  tag=''
  if [ -n "${TAG}" ]; then
    tag="${TAG}"
  elif [ -n "${GIT_BRANCH}" ]; then
    tag="${GIT_BRANCH#origin/tags/}"
  else
    exit 1
  fi
  echo "${tag}"
}
