#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_actual_commit.sh"
  load stub
  ENV_PROPS_FILEPATH="${BATS_TEST_DIRNAME}/tmp/env.properties"
}

teardown() {
  rm_stubs
}

@test "get-actual-commit : is not PR" {
  sha1="abc1234def5678"
  ghprbActualCommit=""
  run main

  # expected env.properties output
  echo ACTUAL_COMMIT="abc1234def5678" > "${BATS_TEST_DIRNAME}"/tmp/expected.env.properties

  echo "${output}"
  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
  [ "$(cmp ${BATS_TEST_DIRNAME}/tmp/env.properties ${BATS_TEST_DIRNAME}/tmp/expected.env.properties)" = "" ]
}

@test "get-actual-commit : is PR" {
  ghprbActualCommit="abc1234def5678"
  run main

  # expected env.properties output
  { echo ACTUAL_COMMIT="abc1234def5678" ; \
    echo VERSION="git-abc1234"; } > "${BATS_TEST_DIRNAME}"/tmp/expected.env.properties

  [ "${status}" -eq 0 ]
  [ "${output}" = "PR build, so using VERSION=git-abc1234 for Docker image tag rather than the merge commit" ]
  [ "$(cmp ${BATS_TEST_DIRNAME}/tmp/env.properties ${BATS_TEST_DIRNAME}/tmp/expected.env.properties)" = "" ]
}
