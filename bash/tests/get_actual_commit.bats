#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_actual_commit.sh"
  load stub

  TEST_PR_SHA="abc1234def567812345678901234567890123456"
  TEST_MERGE_SHA="ghi1234jkl567812345678901234567890123456"
  TEST_MASTER_SHA="mno1234pqr567812345678901234567890123456"
  TEST_REPO_NAME="repo"
}

teardown() {
  rm_stubs
}

@test "get-actual-commit : is not PR" {
  export GIT_BRANCH="origin/master"
  export GIT_COMMIT="${TEST_MASTER_SHA}"

  run get-actual-commit "${TEST_REPO_NAME}"

  [ "${status}" -eq 0 ]
  [ "${output}" == "${TEST_MASTER_SHA}" ]
}

@test "get-actual-commit : is PR" {
  export GIT_BRANCH="PR-123"
  export GIT_COMMIT="${TEST_MERGE_SHA}"

  curl_resp="Merge ${TEST_PR_SHA} into ${TEST_MASTER_SHA}"

  load stubs/tpl/default
  stub curl
  stub docker "$(generate-stub "run" "${curl_resp}")" 0

  run get-actual-commit "${TEST_REPO_NAME}"

  echo "${output}"
  [ "${status}" -eq 0 ]
  [ "${output}" == "${TEST_PR_SHA}" ]
}

@test "get-actual-commit : is PR, use merge sha if GH API curl fails" {
  export GIT_BRANCH="PR-123"
  export GIT_COMMIT="${TEST_MERGE_SHA}"

  stub curl "" 1
  stub docker "" 1

  run get-actual-commit "${TEST_REPO_NAME}"

  echo "${output}"
  [ "${status}" -eq 0 ]
  [ "${output}" == "${TEST_MERGE_SHA}" ]
}
