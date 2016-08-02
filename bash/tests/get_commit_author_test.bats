#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_commit_author.sh"
  load stub
  ENV_PROPS_FILEPATH="${BATS_TEST_DIRNAME}/tmp/env.properties"
}

teardown() {
  rm_stubs
}

@test "get-commit-author" {
  stub git

  # expected env.properties output
  echo COMMIT_AUTHOR="\"\"" > ${BATS_TEST_DIRNAME}/tmp/expected.env.properties

  run main

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
  [ "$(cmp ${BATS_TEST_DIRNAME}/tmp/env.properties ${BATS_TEST_DIRNAME}/tmp/expected.env.properties)" = "" ]
}
