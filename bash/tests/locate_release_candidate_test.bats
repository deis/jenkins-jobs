#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/locate_release_candidate.sh"
  load stub
  stub docker
}

teardown() {
  rm_stubs
}

@test "locate-release-candidate" {
  component="my-component"
  commit="abc1234def5678"
  tag="foo-tag"
  env_file="${BATS_TEST_DIRNAME}/tmp/env.properties"

  # expected env.properties output
  { echo COMPONENT_NAME=my-component; \
    echo COMPONENT_SHA=abc1234def5678; \
    echo RELEASE_TAG=foo-tag; \
    echo MY_COMPONENT_SHA=abc1234def5678; } > ${BATS_TEST_DIRNAME}/tmp/expected.env.properties

  run locate-release-candidate "${component}" "${commit}" "${tag}" "${env_file}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "Locating candidate release image quay.io/deis/my-component:git-abc1234..." ]
  [ "$(cmp ${BATS_TEST_DIRNAME}/tmp/env.properties ${BATS_TEST_DIRNAME}/tmp/expected.env.properties)" = "" ]
}
