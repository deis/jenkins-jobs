#!/usr/bin/env bash

set -eo pipefail

main() {
  if [ -z "${COMPONENT_NAME}" ]; then
    echo "COMPONENT_NAME is empty; no component to promote; exiting..."
    exit 0
  else
    promote-component "${COMPONENT_NAME}" "${COMPONENT_SHA}"
  fi
}

promote-component() {
  local component="${1}"
  local commit="${2}"

  local image_name_and_tag="${component}:git-${commit:0:7}"

  # promote to dockerhub
  local original_image=deisci/"${image_name_and_tag}"
  local promoted_image=deis/"${image_name_and_tag}"

  echo "Promoting ${original_image} to ${promoted_image}"

  docker login -e="${DOCKER_EMAIL}" -u="${DOCKER_USERNAME}" -p="${DOCKER_PASSWORD}"
  docker pull "${original_image}"
  docker tag "${original_image}" "${promoted_image}"
  docker push "${promoted_image}"

  # promote to quay.io
  original_image=quay.io/"${original_image}"
  promoted_image=quay.io/"${promoted_image}"

  echo "Promoting ${original_image} to ${promoted_image}"

  docker login -e="${QUAY_EMAIL}" -u="${QUAY_USERNAME}" -p="${QUAY_PASSWORD}" quay.io
  docker pull "${original_image}"
  docker tag "${original_image}" "${promoted_image}"
  docker push "${promoted_image}"
}

if [ -n "${JENKINS_HOME}" ]; then
  main
fi
