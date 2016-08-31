#!/usr/bin/env bash

set -eo pipefail

clusterator-create() {
  k8s_versions="${1}"

  if [ -z "${VERSION}" ]; then
    echo "Assigning random version for each cluster based on provided list:"
    echo "${k8s_versions}"
  fi

  clusters_created=0
  while [ $clusters_created -lt "$NUMBER_OF_CLUSTERS" ]; do
    cluster_version="${VERSION:-$(echo "${k8s_versions}" | shuf -n 1)}"
    echo "Creating cluster with version: ${cluster_version}..."

    docker run \
      -e GCLOUD_CREDENTIALS="${GCLOUD_CREDENTIALS}" \
      -e NUMBER_OF_CLUSTERS=1 \
      -e NUM_NODES="${NUM_NODES}" \
      -e MACHINE_TYPE="${MACHINE_TYPE}" \
      -e VERSION="${cluster_version}" \
      quay.io/deisci/clusterator:git-b1810a5 create

    (( clusters_created += 1 ))
  done
}
