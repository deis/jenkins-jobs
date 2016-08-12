#!/usr/bin/env bash

set -eo pipefail

get-latest-tag() {
  env_file="${ENV_PROPS_FILEPATH:-"${WORKSPACE}/env.properties"}"

  tag=''
  if [ -n "${TAG}" ]; then
    tag="${TAG}"
  elif [ -n "${GIT_BRANCH}" ]; then
    tag="${GIT_BRANCH#origin/tags/}"

    # make sure tag matches latest; else, exit
    latest_tag="${LATEST_TAG:-$(git tag -l | tail -n1)}"
    if [ "${latest_tag}" != "${tag}" ]; then
      echo SKIP_RELEASE=true > "${env_file}"
      exit 0
    fi
  else
    exit 1
  fi
  echo "${tag}"
}
