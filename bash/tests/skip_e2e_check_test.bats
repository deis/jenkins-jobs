#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/skip_e2e_check.sh"
  load stub
  stub git
}

teardown() {
  rm_stubs
}

@test "check-skip-e2e : no description given" {
  COMMIT_DESCRIPTION=""
  run check-skip-e2e

  [ "${status}" -eq 0 ]
  [ "${output}" == "" ]
}

@test "check-skip-e2e : description with no 'skip e2e'" {
  COMMIT_DESCRIPTION="\
    Just a regular PR here
    Want to run e2e for sure
  "
  run check-skip-e2e

  [ "${status}" -eq 0 ]
  [ "${output}" == "" ]
}

@test "check-skip-e2e : description with 'skip e2e'" {
  COMMIT_DESCRIPTION="\
    Just a regular PR here
    [skip e2e]
  "
  run check-skip-e2e

  [ "${status}" -eq 0 ]
  [ "${output}" == "SKIP_E2E=true" ]
}
