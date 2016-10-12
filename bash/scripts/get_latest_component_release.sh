#!/usr/bin/env bash

set -eo pipefail

get-latest-component-release() {
  component="${1}"

  component_to_curl="${component}"
  if [ "${component}" == "monitor" ]; then
    # just choose one monitor sub-component to lookup latest version
    component_to_curl="grafana"
  fi

  wfm_api_url="https://versions.deis.com/v3/versions/stable/deis-${component_to_curl}/latest"
  echo "Getting latest ${component} release via url: ${wfm_api_url}" >&2

  latest_release="$(curl -f "${wfm_api_url}" | jq '.version.version' | tr -d '"')"

  echo "${latest_release}"
}
