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

  # grab latest tag from GH
  latest_tag="$(curl -sSf "https://api.github.com/repos/deis/${component}/releases/latest" | jq '.tag_name' | tr -d '"')"

  # If tag not latest, bail out
  if [ "${tag}" != "${latest_tag}" ]; then
    echo "Silly Jenkins, ${component} tag '${tag}' has already been released!  Exiting." >&2
    exit 1
  fi

  echo "${tag}"
}
