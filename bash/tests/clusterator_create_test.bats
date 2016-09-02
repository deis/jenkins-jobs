#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/clusterator_create.sh"
  load stub
  stub docker
}

teardown() {
  rm_stubs
}

strip-indent() {
  echo "${1}" | sed 's/^[ \t]*//'
}

@test "clusterator-create : VERSION set" {
  NUMBER_OF_CLUSTERS=2
  VERSION=1.2.3

  run clusterator-create ""

  expected_output="\
    Creating cluster with version: 1.2.3...
    Creating cluster with version: 1.2.3..."

  [ "${status}" -eq 0 ]
  [ "${output}" == "$(strip-indent "${expected_output}")" ]
}

@test "clusterator-create : VERSION not set" {
  NUMBER_OF_CLUSTERS=1
  k8s_versions="\
    1.2.6
    1.3.4
    1.3.5"

  stub shuf "echo random_version"

  run clusterator-create "$(strip-indent "${k8s_versions}")"

  expected_output="\
    Assigning random version for each cluster based on provided list:
    ${k8s_versions}
    Creating cluster with version: random_version..."

  [ "${status}" -eq 0 ]
  [ "${output}" == "$(strip-indent "${expected_output}")" ]
}
