#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/update_commit_status.sh"
  load stub
  stub curl
}

teardown() {
  rm_stubs
}

strip-ws() {
  echo -e "${1}" | tr -d '[[:space:]]'
}

@test "update-commit-status" {
  commit_status="status"
  repo_name="foo-repo"
  git_commit="abc1234"
  target_url="target.url"
  description="description"

  run update-commit-status \
    "${commit_status}" \
    "${repo_name}" \
    "${git_commit}" \
    "${target_url}" \
    "${description}"

  expected_data='
    {"state":"'"${commit_status}"'",
    "target_url":"'"${target_url}"'",
    "description":"'"${description}"'",
    "context":"ci/jenkins/pr"}
  '

  expected_output='
    Updating commit '\'${git_commit}\'' in repo '\'${repo_name}\'' with the following data:
    '${expected_data}'
  '

  [ "${status}" -eq 0 ]
  [ "$(strip-ws "${output}")" == "$(strip-ws "${expected_output}")" ]
}
