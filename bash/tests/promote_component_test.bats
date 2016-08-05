#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/promote_component.sh"
  load stub
  stub docker
}

teardown() {
  rm_stubs
}

@test "main : component name empty" {
  run main

  [ "${status}" -eq 0 ]
  [ "${output}" = "COMPONENT_NAME is empty.  No component to promote; exiting..." ]
}

@test "main : promote component" {
  export COMPONENT_NAME='workflow-e2e'
  export COMPONENT_SHA='abc1234def5678'

  expected_original_image='deisci/workflow-e2e:git-abc1234'
  expected_promoted_image='deis/workflow-e2e:git-abc1234'

  run main

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "Promoting '${expected_original_image}' to '${expected_promoted_image}'" ]
  [ "${lines[1]}" = "Promoting 'quay.io/${expected_original_image}' to 'quay.io/${expected_promoted_image}'" ]
}
