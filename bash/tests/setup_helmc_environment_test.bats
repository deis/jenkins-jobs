#!/usr/bin/env bats

# helmc-remove (entire file)
setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/setup_helmc_environment.sh"
  load stub
  stub jq
  stub curl
}

teardown() {
  rm_stubs
}

# setup-helmc-env tests

@test "setup-helmc-env: CHARTS_SHA empty" {
  CHARTS_SHA=""

  run setup-helmc-env "${TEST_ENV_FILE}"

  [ "${status}" -eq 0 ]
  [ "${output}" == "" ]
}

@test "setup-helmc-env: CHARTS_SHA populated" {
  CHARTS_SHA="abc1234def5678"
  repo_name="repo"
  helmc_remote_repo="https://some/remote/charts/${repo_name}.git"

  stub grep "echo ${helmc_remote_repo}" 0

  run setup-helmc-env

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" == "HELM_REMOTE_REPO=${helmc_remote_repo}" ]
  [ "${lines[1]}" == "WORKFLOW_BRANCH=${CHARTS_SHA}" ]
  [ "${lines[2]}" == "WORKFLOW_E2E_BRANCH=${CHARTS_SHA}" ]
}

# get-remote-repo-url tests

@test "get-remote-repo-url: commit info not found" {
  repo_name="repo-a"
  git_commit="abc1234def5678"

  run get-remote-repo-url "${repo_name}" "${git_commit}"

  [ "${status}" -eq 0 ]
  [ "${output}" == "" ]
}

@test "get-remote-repo-url: correct repo found, still pr" {
  repo_name="repo-a"
  git_commit="abc1234def5678"

  urls="\
\"https://github.com/user/foo.git\"
\"https://github.com/user/${repo_name}.git\"
"
  stub jq "echo '${urls}'"

  run get-remote-repo-url "${repo_name}" "${git_commit}"

  [ "${output}" == "https://github.com/user/${repo_name}.git" ]
}

@test "get-remote-repo-url: correct repo found, merged" {
  repo_name="repo-a"
  git_commit="abc1234def5678"

  # We check committer name; if GitHub, assume it had been merged
  stub jq "echo 'GitHub'"

  run get-remote-repo-url "${repo_name}" "${git_commit}"

  [ "${output}" == "https://github.com/deis/${repo_name}.git" ]
}
