#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_actual_commit.sh"
  load stub

  TEST_GIT_SHA="abc1234def567812345678901234567890123456"
}

teardown() {
  rm_stubs
}

@test "get-actual-commit : is not PR" {
  export GIT_BRANCH="origin/master"
  export GIT_COMMIT="${TEST_GIT_SHA}"

  run get-actual-commit

  [ "${status}" -eq 0 ]
  [ "${output}" == "${TEST_GIT_SHA}" ]
}

@test "get-actual-commit : is PR, multiple parents" {
  export GIT_BRANCH="PR-123"
  export GIT_COMMIT="${TEST_GIT_SHA}"
  export GIT_COMMIT_PARENTS="ghi1234jkl567812345678901234567890123456 mno1234pqr567812345678901234567890123456"

  run get-actual-commit

  [ "${status}" -eq 0 ]
  [ "${output}" == "ghi1234jkl567812345678901234567890123456" ]
}

@test "get-actual-commit : is PR, single parent" {
  export GIT_BRANCH="PR-123"
  export GIT_COMMIT="${TEST_GIT_SHA}"
  export GIT_COMMIT_PARENTS="${TEST_GIT_SHA}"

  run get-actual-commit

  [ "${status}" -eq 0 ]
  [ "${output}" == "${TEST_GIT_SHA}" ]
}
