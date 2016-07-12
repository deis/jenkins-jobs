#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_actual_commit.sh"
}

@test "get-actual-commit : is not PR" {
  ghprbActualCommit=""
  run main

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "get-actual-commit : is PR" {
  ghprbActualCommit="abc1234def5678"
  run main

  [ "${status}" -eq 0 ]
  [ "${output}" = "PR build, so using VERSION=git-abc1234 for Docker image tag rather than the merge commit" ]
}
