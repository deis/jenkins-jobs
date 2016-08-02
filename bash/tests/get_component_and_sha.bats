#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_component_and_sha.sh"
  load stub
  ENV_PROPS_FILEPATH="${BATS_TEST_DIRNAME}/tmp/env.properties"
}

teardown() {
  rm_stubs
}

@test "main : gets component and sha" {
  export MY_COMPONENT_SHA="abc1234def5678"
  run main

  # expected env.properties
  { echo COMPONENT_NAME="my-component"; \
    echo COMPONENT_SHA="abc1234def5678"; } >> "${BATS_TEST_DIRNAME}/tmp/expected.env.properties"

  [ "${status}" -eq 0 ]
  [ "${output}" = "Found component 'my-component' with commit sha 'abc1234def5678'" ]
  [ "$(cmp ${BATS_TEST_DIRNAME}/tmp/env.properties ${BATS_TEST_DIRNAME}/tmp/expected.env.properties)" = "" ]
}

@test "main : skip promotion if workflow-cli" {
  export WORKFLOW_CLI_SHA="abc1234def5678"
  run main

  # expected env.properties
  { echo SKIP_COMPONENT_PROMOTE=true; \
    echo COMPONENT_NAME="workflow-cli"; \
    echo COMPONENT_SHA="abc1234def5678"; } >> "${BATS_TEST_DIRNAME}/tmp/expected.env.properties"

  [ "${status}" -eq 0 ]
  [ "${output}" = "Found component 'workflow-cli' with commit sha 'abc1234def5678'" ]
  [ "$(cmp ${BATS_TEST_DIRNAME}/tmp/env.properties ${BATS_TEST_DIRNAME}/tmp/expected.env.properties)" = "" ]
}
