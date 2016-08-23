#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/locate_release_candidate.sh"
  load stub
  stub docker
}

teardown() {
  rm_stubs
}

@test "locate-release-candidate : candidate found" {
  component="my-component"
  commit="abc1234def5678"
  tag="foo-tag"

  run locate-release-candidate "${component}" "${commit}" "${tag}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "COMPONENT_NAME=my-component" ]
  [ "${lines[1]}" = "COMPONENT_SHA=abc1234def5678" ]
  [ "${lines[2]}" = "RELEASE_TAG=foo-tag" ]
  [ "${lines[3]}" = "MY_COMPONENT_SHA=abc1234def5678" ]
}

@test "locate-release-candidate : candidate not found" {
  component="my-component"
  commit="abc1234def5678"
  tag="foo-tag"

  stub docker '' 1

  run locate-release-candidate "${component}" "${commit}" "${tag}"
  echo "${output}"
  [ "${status}" -eq 1 ]
  [ "${output}" = "Release candidate 'quay.io/deis/my-component:git-abc1234' cannot be located; exiting." ]
}
