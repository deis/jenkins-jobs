#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_component_and_sha.sh"
}

@test "main : gets component and sha" {
  export MY_COMPONENT_SHA="abc1234def5678"
  run main
  echo "${output}"
  [ "${status}" -eq 0 ]
  [ "${output}" = "Found component 'my-component' with commit sha 'abc1234def5678'" ]
}

@test "main : does not get component and sha if workflow-cli" {
  export WORKFLOW_CLI_SHA="abc1234def5678"
  run main
  echo "${output}"
  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}
