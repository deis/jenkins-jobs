#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/run_e2e.sh"
  load stub
  stub docker
  stub mkdir

  E2E_DIR="${BATS_TEST_DIRNAME}/tmp"
  E2E_DIR_LOGS="${E2E_DIR}/logs"
  RELEASE="dev"
}

teardown() {
  rm_stubs
}

@test "run-e2e : bogus default env file" {

  run run-e2e "bogus/default/env/file"

  [ "${status}" -eq 0 ]
  [ "${output}" == "" ]
  [[ -e "${E2E_DIR_LOGS}" ]]
  [[ "$(cat "${E2E_DIR}/env.file")" == *"CLI_VERSION=latest"* ]]
}

@test "run-e2e : real default env file" {
  default_env_file="${BATS_TEST_DIRNAME}/tmp/default_env.file"

  echo "FOO=bar" > "${default_env_file}"

  run run-e2e "${default_env_file}"

  [ "${status}" -eq 0 ]
  [ "${output}" == "" ]
  [ -e "${E2E_DIR_LOGS}" ]
  [[ "$(cat "${E2E_DIR}/env.file")" == *"CLI_VERSION=latest"*"FOO=bar"* ]]
}

@test "run-e2e : WORKFLOW_CLI_SHA set" {
  WORKFLOW_CLI_SHA="abc1234def5678"

  run run-e2e "bogus/default/env/file"

  [ "${status}" -eq 0 ]
  [ "${output}" == "" ]
  [ -e "${E2E_DIR_LOGS}" ]
  [[ "$(cat "${E2E_DIR}/env.file")" == *"CLI_VERSION=abc1234"* ]]
}


@test "run-e2e : COMPONENT_REPO and ACTUAL_COMMIT set" {
  COMPONENT_REPO=workflow-e2e
  ACTUAL_COMMIT="abc1234def5678"

  run run-e2e "bogus/default/env/file"

  [ "${status}" -eq 0 ]
  [ "${output}" == "" ]
  [ -e "${E2E_DIR_LOGS}" ]
  [[ "$(cat "${E2E_DIR}/env.file")" == *"WORKFLOW_E2E_SHA=abc1234"* ]]
}
