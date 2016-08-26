#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_component_and_sha.sh"
}

@test "get-component-and-sha : gets component and sha" {
  export MY_COMPONENT_SHA="abc1234def5678"

  run get-component-and-sha

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" == "COMPONENT_NAME=my-component" ]
  [ "${lines[1]}" == "COMPONENT_SHA=abc1234def5678" ]
}

@test "get-component-and-sha : skip promotion if workflow-cli" {
  export WORKFLOW_CLI_SHA="abc1234def5678"

  run get-component-and-sha

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" == "SKIP_COMPONENT_PROMOTE=true" ]
  [ "${lines[1]}" == "COMPONENT_NAME=workflow-cli" ]
  [ "${lines[2]}" == "COMPONENT_SHA=abc1234def5678" ]
}
