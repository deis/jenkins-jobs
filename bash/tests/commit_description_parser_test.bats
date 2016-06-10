#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/commit_description_parser.sh"
}

@test "parse-commit-description : no description given" {
  description=""
  run parse-commit-description "${description}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "parse-commit-description : description with no requirements" {
  description="\
    Just a regular PR here
    No required commits...
  "
  run parse-commit-description "${description}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "parse-commit-description : description with requirements" {
  description="\
    A PR with required commits...
    Requires repo-a#abc1234
    requires repo-b#def5678
  "
  run parse-commit-description "${description}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "REPO_A_SHA=abc1234" ]
  [ "${lines[1]}" = "REPO_B_SHA=def5678" ]
}

@test "parse-commit-description : description with ill-formated requirements" {
  description="\
    A PR with required commits...
    Requirez repo-a#abc1234
    reqs repo-b#def5678
  "
  run parse-commit-description "${description}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "main : if on jenkins in jenkins-jobs workspace" {
  export JENKINS_HOME="foo"
  export JOB_BASE_NAME="jenkins-jobs"
  run main

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "main : if on jenkins and not in jenkins-jobs workspace" {
  export JENKINS_HOME="foo"
  export JOB_BASE_NAME="bar"
  run main

  echo "${output}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"Permission denied"* ]]
}
