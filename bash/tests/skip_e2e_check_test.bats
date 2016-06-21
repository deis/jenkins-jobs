#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/skip_e2e_check.sh"
}

@test "check-skip-e2e : no description given" {
  description=""
  run check-skip-e2e "${description}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "check-skip-e2e : description with no 'skip e2e'" {
  description="\
    Just a regular PR here
    Want to run e2e for sure
  "
  run check-skip-e2e "${description}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "check-skip-e2e : description with 'skip e2e'" {
  description="\
    Just a regular PR here
    [skip e2e]
  "
  run check-skip-e2e "${description}"

  [ "${status}" -eq 1 ]
  [ "${output}" = "'skip e2e' found in commit body so skipping e2e test run" ]
}
