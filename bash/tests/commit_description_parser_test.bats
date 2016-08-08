#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/commit_description_parser.sh"
  load stub
  ENV_PROPS_FILEPATH="${BATS_TEST_DIRNAME}/tmp/env.properties"
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

  # expected env.properties
  { echo REPO_A_SHA="${sha}"; \
    echo REPO_B_SHA="${sha}"; } > "${BATS_TEST_DIRNAME}/tmp/expected.env.properties"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "Found Required PR(s)..." ]
  [ "${lines[1]}" = "repo-a#1" ]
  [ "${lines[2]}" = "repo-b#2" ]
  [ "${lines[5]}" = "REPO_A_SHA=${sha}" ]
  [ "${lines[8]}" = "REPO_B_SHA=${sha}" ]
  [ "$(cmp ${BATS_TEST_DIRNAME}/tmp/env.properties ${BATS_TEST_DIRNAME}/tmp/expected.env.properties)" = "" ]
}

@test "parse-commit-description : description with requirements and full 'deis/<repo>#<pr number>' format" {
  description="\
    A PR with required commits...
    Requires deis/repo-e2e#1
    requires deis/repo-b#2
  "
  sha="abc1234"
  stub curl "echo '[{\"sha\":\"${sha}\"}]'"
  stub docker "echo ${sha}"

  run parse-commit-description "${description}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "Found Required PR(s)..." ]
  [ "${lines[1]}" = "repo-e2e#1" ]
  [ "${lines[2]}" = "repo-b#2" ]
  [ "${lines[5]}" = "REPO_E2E_SHA=${sha}" ]
  [ "${lines[8]}" = "REPO_B_SHA=${sha}" ]
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

@test "parse-commit-description : description with requirement commits not found" {
  repoName="repo-a"
  pr="1"
  description="\
    A PR with required commits...
    Requires ${repoName}#${pr}
  "

  stub curl
  stub docker # nothing returned if no commits found

  run parse-commit-description "${description}"

  [ "${status}" -eq 1 ]
  [ "${lines[0]}" = "Found Required PR(s)..." ]
  [ "${lines[1]}" = "repo-a#1" ]
  [ "${lines[2]}" = "Failure: Commit sha for PR #${pr} in repo '${repoName}' not found!" ]
}

# get-pr-commits tests

@test "get-pr-commits: no commits found" {
  repoName="repo-a"
  prNumber="1"

  stub curl
  stub docker

  run get-pr-commits "${repoName}" "${prNumber}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "" ]
}

@test "get-pr-commits: commits found" {
  repoName="repo-a"
  prNumber="1"

  sha="abc1234"
  stub curl
  stub docker "echo '${sha}'"

  run get-pr-commits "${repoName}" "${prNumber}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "${sha}" ]
}

# main tests

@test "main : commit and description found" {
  description="\
    A PR with required commits...
    Requires deis/repo-e2e#1
    requires deis/repo-b#2
  "
  export COMMIT_DESCRIPTION="${description}"
  export ACTUAL_COMMIT="bogus_actual_commit"
  stub git
  sha="abc1234"
  stub curl
  stub docker "echo ${sha}"

  run main

  # expected env.properties
  { echo REPO_E2E_SHA="${sha}"; \
    echo REPO_B_SHA="${sha}"; } > "${BATS_TEST_DIRNAME}/tmp/expected.env.properties"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" = "Found Required PR(s)..." ]
  [ "${lines[1]}" = "repo-e2e#1" ]
  [ "${lines[2]}" = "repo-b#2" ]
  [ "$(cmp ${BATS_TEST_DIRNAME}/tmp/env.properties ${BATS_TEST_DIRNAME}/tmp/expected.env.properties)" = "" ]
}
