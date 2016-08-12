#!/usr/bin/env bash

set -eo pipefail

main() {
  retag-release-candidate "${COMPONENT_NAME}" "${COMPONENT_SHA}"
}

retag-release-candidate() {
  local candidate="${1}"
  local commit="${2}"
  local image_name="deis/${candidate}"

  # release to dockerhub
  local candidate_image="${image_name}":git-"${commit:0:7}"
  local released_image="${image_name}":"${RELEASE_TAG}"

  echo "Retagging ${candidate_image} to ${released_image}"

  docker login -e="${DOCKER_EMAIL}" -u="${DOCKER_USERNAME}" -p="${DOCKER_PASSWORD}"
  docker pull "${candidate_image}"
  docker tag "${candidate_image}" "${released_image}"
  docker push "${released_image}"

  # release to quay.io
  candidate_image=quay.io/"${candidate_image}"
  released_image=quay.io/"${released_image}"

  echo "Retagging ${candidate_image} to ${released_image}"

  docker login -e="${QUAY_EMAIL}" -u="${QUAY_USERNAME}" -p="${QUAY_PASSWORD}" quay.io
  docker pull "${candidate_image}"
  docker tag "${candidate_image}" "${released_image}"
  docker push "${released_image}"
}

if [ -n "${JENKINS_HOME}" ]; then
  main
fi
