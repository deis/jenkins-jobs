#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/find_required_commits.sh"
  load stub
  stub git
  stub curl

  TEST_GIT_COMMIT="abc1234def5678"
}

teardown() {
  rm_stubs
}

# find-required-commits tests

@test "find-required-commits : no description given" {
  COMMIT_DESCRIPTION=""

  run find-required-commits "${TEST_GIT_COMMIT}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "find-required-commits : description with no requirements" {
  COMMIT_DESCRIPTION="\
    Just a regular PR here
    No required commits...
  "

  run find-required-commits "${TEST_GIT_COMMIT}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "find-required-commits : description with requirements" {
  COMMIT_DESCRIPTION="\
    A PR with required commits...
    Requires repo-a#1
    requires repo-b#2
  "
  required_commit="abc1234"
  stub docker "echo ${required_commit}"

  run find-required-commits "${TEST_GIT_COMMIT}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "REPO_A_SHA=${required_commit}" ]
  [ "${lines[1]}" = "REPO_B_SHA=${required_commit}" ]
}

@test "find-required-commits : description with requirements and full 'deis/<repo>#<pr number>' format" {
  COMMIT_DESCRIPTION="\
    A PR with required commits...
    Requires deis/repo-e2e#1
    requires deis/repo-b#2
  "
  required_commit="abc1234"
  stub docker "echo ${required_commit}"

  run find-required-commits "${TEST_GIT_COMMIT}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "REPO_E2E_SHA=${required_commit}" ]
  [ "${lines[1]}" = "REPO_B_SHA=${required_commit}" ]
}

@test "find-required-commits : description with ill-formated requirements" {
  COMMIT_DESCRIPTION="\
    A PR with required commits...
    Requirez repo-a#1
    reqs repo-b#2
  "
  run find-required-commits "${TEST_GIT_COMMIT}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "find-required-commits : description with requirement commits not found" {
  repo_name="repo-a"
  pr="1"
  COMMIT_DESCRIPTION="\
    A PR with required commits...
    Requires ${repo_name}#${pr}
  "
  stub docker # nothing returned if no commits found

  run find-required-commits "${TEST_GIT_COMMIT}"

  [ "${status}" -eq 1 ]
  [ "${lines[0]}" = "Failure: Commit sha for PR #${pr} in repo '${repo_name}' not found!" ]
}

# get-pr-commits tests

@test "get-pr-commits: no commits found" {
  repoName="repo-a"
  prNumber="1"

  stub docker "" 1

  run get-pr-commits "${repoName}" "${prNumber}"

  [ "${status}" -eq 1 ]
  [ "${output}" = "" ]
}

@test "get-pr-commits: commits found" {
  repoName="repo-a"
  prNumber="1"

  sha="abc1234"
  stub docker "echo '${sha}'"

  run get-pr-commits "${repoName}" "${prNumber}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "${sha}" ]
}
