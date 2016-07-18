#!/usr/bin/env bats

setup() {
  load stub
  stub curl
  stub git "echo $@"
}

teardown() {
  rm_stubs
}

setupMain() {
  export WORKFLOW_VERSION="${1}"
  milestone="${2}"
  stub docker "echo ${milestone}"
  . "${BATS_TEST_DIRNAME}/../scripts/milestone_watcher.sh"
}

@test "main : milestone does not exist" {
  setupMain "currentVersion"
  run main

  [ "${status}" -eq 0 ]
  [ "${output}" = "No milestone found from the deis/workflow repo!" ]
}

@test "main : milestone exists and does not match current" {
  setupMain "currentVersion" "newMilestone"
  run main

  [ "${status}" -eq 0 ]
  [ "${output}" = "Bumping current Workflow version from 'currentVersion' to 'newMilestone'..." ]
}

@test "main : milestone exists and matches current" {
  setupMain "currentVersion" "currentVersion"
  run main

  [ "${status}" -eq 0 ]
  [ "${output}" = "Current Workflow version 'currentVersion' already matches 'currentVersion'." ]
}
