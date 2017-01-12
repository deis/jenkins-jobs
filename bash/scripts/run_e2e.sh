#!/usr/bin/env bash
set -eo pipefail

run-e2e() {
  local default_env_file="${1}"

  export CLI_VERSION="${CLI_VERSION:-latest}"
  if [ -n "${WORKFLOW_CLI_SHA}" ]; then
    export CLI_VERSION="${WORKFLOW_CLI_SHA:0:7}"
  fi

  mkdir -p "${E2E_DIR_LOGS}"
  env > "${E2E_DIR}"/env.file
  if [ -e "${default_env_file}" ]; then
    cat "${default_env_file}" >> "${E2E_DIR}"/env.file
  fi

  local image_tag="canary"
  if [ -n "${E2E_RUNNER_SHA}" ]; then
    image_tag="git-${E2E_RUNNER_SHA:0:7}"
  fi
  image="quay.io/deisci/e2e-runner:${image_tag}"

  docker pull "${image}" # bust the cache as tag may be canary
  docker run \
    -u jenkins:jenkins \
    --env-file="${E2E_DIR}"/env.file \
    -v "${E2E_DIR_LOGS}":/home/jenkins/logs:rw "${image}"
}
