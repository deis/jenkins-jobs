#!/usr/bin/env bash

set -eo pipefail

locate-release-candidate() {
  local component="${1}"
  local commit="${2}"
  local tag="${3}"

  component_env_var="$(echo "${component}" | perl -ne 'print uc' | sed 's/-/_/g')"_SHA
  candidate_image=quay.io/deis/"${component}":git-"${commit:0:7}"

  docker pull "${candidate_image}"

  if [ $? -ne 0 ]; then
    echo "Release candidate '${candidate_image}' cannot be located; exiting." 1>&2
    exit 1
  fi

  { echo COMPONENT_NAME="${component}"; \
    echo COMPONENT_SHA="${commit}"; \
    echo RELEASE_TAG="${tag}"; \
    echo "${component_env_var}"="${commit}"; }
}
