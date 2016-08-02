#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/locate_release_candidate.sh"
  load stub
  stub docker
}

teardown() {
  rm_stubs
}

@test "main : TAG and GIT_BRANCH not set" {
  run main

  [ "${status}" -eq 1 ]
  [ "${output}" = "GIT_BRANCH or TAG not set, cannot determine tag to release; exiting." ]
}

@test "main : TAG not set - GIT_BRANCH set - does not match latest" {
  export GIT_BRANCH="origin/tags/foo-tag"
  export LATEST_COMPONENT_TAG="bar-tag"
  export COMPONENT_NAME="my-component"
  export COMPONENT_SHA="abc1234def5678"

  run main

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "GIT_BRANCH set to 'origin/tags/foo-tag', attempting release of this tag..." ]
  [ "${lines[1]}" = "Latest tag of 'bar-tag' does not match 'foo-tag'; not proceeding with release." ]
  [ "$(cat ${BATS_TEST_DIRNAME}/tmp/env.properties)" = "SKIP_RELEASE=true" ]
}

@test "main : TAG not set - GIT_BRANCH set - matches latest" {
  export GIT_BRANCH="origin/tags/foo-tag"
  export LATEST_COMPONENT_TAG="foo-tag"
  export COMPONENT_NAME="my-component"
  export COMPONENT_SHA="abc1234def5678"

  # expected env.properties output
  { echo COMPONENT_NAME=my-component; \
    echo COMPONENT_SHA=abc1234def5678; \
    echo RELEASE_TAG=foo-tag; \
    echo MY_COMPONENT_SHA=abc1234def5678; } > ${BATS_TEST_DIRNAME}/tmp/expected.env.properties

  run main

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "GIT_BRANCH set to 'origin/tags/foo-tag', attempting release of this tag..." ]
  [ "${lines[1]}" = "Locating candidate release image quay.io/deis/my-component:git-abc1234..." ]
  [ "$(cmp ${BATS_TEST_DIRNAME}/tmp/env.properties ${BATS_TEST_DIRNAME}/tmp/expected.env.properties)" = "" ]
}

@test "main : TAG set" {
  export TAG="foo-tag"
  export GIT_BRANCH="origin/tags/foo-tag"
  export COMPONENT_NAME="my-component"
  export COMPONENT_SHA="abc1234def5678"

  # expected env.properties output
  { echo COMPONENT_NAME=my-component; \
    echo COMPONENT_SHA=abc1234def5678; \
    echo RELEASE_TAG=foo-tag; \
    echo MY_COMPONENT_SHA=abc1234def5678; } > ${BATS_TEST_DIRNAME}/tmp/expected.env.properties

  run main

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "TAG set to 'foo-tag', attempting release of this tag..." ]
  [ "${lines[1]}" = "Locating candidate release image quay.io/deis/my-component:git-abc1234..." ]
  [ "$(cmp ${BATS_TEST_DIRNAME}/tmp/env.properties ${BATS_TEST_DIRNAME}/tmp/expected.env.properties)" = "" ]
}
