#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/retag_release_candidate.sh"
  load stub
  stub docker
}

teardown() {
  rm_stubs
}

@test "main" {
  export COMPONENT_NAME="my-component"
  export COMPONENT_SHA="abc1234def5678"
  export RELEASE_TAG="v9.9.9"

  run main

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "Retagging deis/my-component:git-abc1234 to deis/my-component:v9.9.9" ]
  [ "${lines[1]}" = "Retagging quay.io/deis/my-component:git-abc1234 to quay.io/deis/my-component:v9.9.9" ]
}
