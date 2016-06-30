#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/check_release_branch.sh"
}

@test "check-release-branch : is non-release branch" {
  export RELEASE='foo'
  export GIT_BRANCH='origin/master'
  run check-release-branch

  [ "${status}" -eq 0 ]
  [ "${output}" = "GIT_BRANCH (${GIT_BRANCH}) is not 'origin/release-${RELEASE}'; exiting build." ]
}

@test "check-release-branch : is release branch" {
  export RELEASE='foo'
  export GIT_BRANCH="origin/release-${RELEASE}"
  run check-release-branch

  [ "${status}" -eq 0 ]
  [ "${output}" = "Proceeding with build/deploy from 'origin/release-${RELEASE}'..." ]
}
