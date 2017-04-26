#!/usr/bin/env bash

set -eo pipefail

get-latest-tag() {
  component="${1}"

  tag=''
  if [ -n "${TAG}" ]; then
    tag="${TAG}"
  elif [ -n "${GIT_BRANCH}" ]; then
    tag="${GIT_BRANCH#origin/tags/}"
  else
    exit 1
  fi

  # If tag already released, bail out
  if curl -sSf "https://versions.deis.com/v3/versions/stable/deis-${component}/${tag}"; then
    echo "Silly Jenkins, ${component} tag '${tag}' has already been released!  Exiting." >&2
    exit 1
  fi

  echo "${tag}"
}
