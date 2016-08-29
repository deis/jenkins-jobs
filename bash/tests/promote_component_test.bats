#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/promote_component.sh"
  load stub
  stub docker

  STAGING_ORG="deisci"
  PROD_ORG="deis"
}

teardown() {
  rm_stubs
}

@test "main : COMPONENT_NAME empty" {
  run main

  [ "${status}" -eq 0 ]
  [ "${output}" == "COMPONENT_NAME is empty; no component to promote; exiting..." ]
}

@test "main : COMPONENT_NAME non-empty" {
  export COMPONENT_NAME="my-component"
  export COMPONENT_SHA="abc1234def5678"

  run main

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" == "Promoting ${STAGING_ORG}/my-component:git-abc1234 to ${PROD_ORG}/my-component:git-abc1234" ]
  [ "${lines[1]}" == "Promoting quay.io/${STAGING_ORG}/my-component:git-abc1234 to quay.io/${PROD_ORG}/my-component:git-abc1234" ]
}
