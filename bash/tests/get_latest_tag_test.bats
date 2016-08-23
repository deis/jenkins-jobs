#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_latest_tag.sh"
  load stub
  stub docker
}

teardown() {
  rm_stubs
}

@test "main : TAG and GIT_BRANCH not set" {
  run get-latest-tag

  [ "${status}" -eq 1 ]
  [ "${output}" = "" ]
}

@test "main : TAG not set - GIT_BRANCH set - matches latest" {
  export GIT_BRANCH="origin/tags/foo-tag"
  export LATEST_TAG="foo-tag"

  run get-latest-tag

  [ "${status}" -eq 0 ]
  [ "${output}" = "${LATEST_TAG}" ]
}

@test "main : TAG set" {
  export TAG="foo-tag"
  export GIT_BRANCH="origin/tags/foo-tag"

  run get-latest-tag

  [ "${status}" -eq 0 ]
  [ "${output}" = "${TAG}" ]
}
