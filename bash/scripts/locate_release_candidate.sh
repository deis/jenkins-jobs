#!/usr/bin/env bash

set -eo pipefail

locate-release-candidate() {
  local component="${1}"
  local commit="${2}"
  local tag="${3}"
  local env_file="${4}"

  mk-env-file-path "${env_file}"

  component_env_var="$(echo "${component}" | perl -ne 'print uc' | sed 's/-/_/g')"_SHA
  candidate_image=quay.io/deis/"${component}":git-"${commit:0:7}"

  echo "Locating candidate release image ${candidate_image}..."
  docker pull "${candidate_image}"

  { echo COMPONENT_NAME="${component}"; \
    echo COMPONENT_SHA="${commit}"; \
    echo RELEASE_TAG="${tag}"; \
    echo "${component_env_var}"="${commit}"; } > "${env_file}"
}

mk-env-file-path() {
  if [ ! -d "$(dirname "${env_file}")" ]; then
    mkdir -p "$(dirname "${env_file}")"
  fi
}
