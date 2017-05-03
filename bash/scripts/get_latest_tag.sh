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

  # grab all released tags from GH
  released_tags="$(curl -sSf "https://api.github.com/repos/deis/${component}/releases" | jq '.[].tag_name')"

  # If tag already released, bail out
  if [[ "${released_tags}" == *"${tag}"* ]]; then
    echo "Silly Jenkins, ${component} tag '${tag}' has already been released!  Exiting." >&2
    exit 1
  fi

  echo "${tag}"
}
