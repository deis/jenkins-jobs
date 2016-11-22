#!/usr/bin/env bash
set -eo pipefail

run-e2e() {
  local default_env_file="${1}"

  # begin helmc-remove
  if [ -n "${RELEASE}" ]; then
    export WORKFLOW_CHART="workflow-${RELEASE}"
    export WORKFLOW_E2E_CHART="workflow-${RELEASE}-e2e"
  fi
  # end helmc-remove

  export CLI_VERSION="${CLI_VERSION:-latest}"
  if [ -n "${WORKFLOW_CLI_SHA}" ]; then
    export CLI_VERSION="${WORKFLOW_CLI_SHA:0:7}"
  fi

  mkdir -p "${E2E_DIR_LOGS}"
  env > "${E2E_DIR}"/env.file
  if [ -e "${default_env_file}" ]; then
    cat "${default_env_file}" >> "${E2E_DIR}"/env.file
  fi

  docker pull "${E2E_RUNNER_IMAGE}" # bust the cache as tag may be canary
  docker run \
    -u jenkins:jenkins \
    --env-file="${E2E_DIR}"/env.file \
    -v "${E2E_DIR_LOGS}":/home/jenkins/logs:rw "${E2E_RUNNER_IMAGE}"
}
