#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/commit_description_parser.sh"
  load stub
}

teardown() {
  rm_stubs
}

# parse-commit-description tests

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
    Requires repo-a#1
    requires repo-b#2
  "
  sha="abc1234"
  stub curl "echo '[{\"sha\":\"${sha}\"}]'"
  stub docker "echo ${sha}"

  run parse-commit-description "${description}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "REPO_A_SHA=${sha}" ]
  [ "${lines[1]}" = "REPO_B_SHA=${sha}" ]
}

@test "parse-commit-description : description with ill-formated requirements" {
  description="\
    A PR with required commits...
    Requirez repo-a#1
    reqs repo-b#2
  "
  run parse-commit-description "${description}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "parse-commit-description : description with requirements not found" {
  repoName="repo-a"
  pr="1"
  description="\
    A PR with required commits...
    Requires ${repoName}#${pr}
  "
  notFoundMsg=cat <<EOF
    {
      "message": "Not Found",
      "documentation_url": "https://developer.github.com/v3"
    }
EOF
  stub curl "echo ${notFoundMsg}"
  stub docker "echo ${notFoundMsg}"

  run parse-commit-description "${description}"

  [ "${status}" -eq 1 ]
  [ "${output}" = "Commit sha for PR #${pr} in repo '${repoName}' not found." ]
}

# get-most-recent-pr-commit tests

@test "get-most-recent-pr-commit: no commits found" {
  repoName="repo-a"
  prNumber="1"

  stub curl
  stub docker

  run get-most-recent-pr-commit "${repoName}" "${prNumber}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "get-most-recent-pr-commit: one commit" {
  repoName="repo-a"
  prNumber="1"

  sha="abc1234"
  stub curl
  stub docker "echo '${sha}'"

  run get-most-recent-pr-commit "${repoName}" "${prNumber}"
  echo "${status}"
  echo "${output}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "${sha}" ]
}

@test "get-most-recent-pr-commit: multiple commits" {
  repoName="repo-a"
  prNumber="1"

  sha1="abc1234"
  sha2="def5678"
  stub curl
  stub docker "printf '${sha1}\n${sha2}'"

  run get-most-recent-pr-commit "${repoName}" "${prNumber}"
  echo "${status}"
  echo "${output}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "${sha2}" ]
}

# main tests

@test "main : if not on jenkins" {
  run main

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "main : if on jenkins" {
  export JENKINS_HOME="foo"
  run main

  echo "${output}"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"Permission denied"* ]]
}
