#!/usr/bin/env bash

set -eo pipefail

get-actual-commit() {
  repo_name="${1}"
  ghprb_actual_commit="${2}"
  git_branch="${GIT_BRANCH:-$(git describe --all)}"
  git_commit="${GIT_COMMIT:-$(git rev-parse HEAD)}"

  local pr_commit
  if [[ "${git_branch}" != *"master"* ]]; then
    if [ -n "${ghprb_actual_commit}" ];then
      pr_commit="${ghprb_actual_commit}"
    else
      pr_commit="$(get-pr-commit "${repo_name}" "${git_commit}")"
    fi
  fi
  echo "${pr_commit:-${git_commit}}"

}

get-pr-commit() {
  repo_name="${1}"
  commit="${2}"

  # if pr, commit will be the merge commit with pr commit and master commit as its parents
  # curl GH api to derive actual pr commit for reporting status and Docker image tagging
  resp=$(curl \
  -sSL \
  --user deis-admin:"${GITHUB_ACCESS_TOKEN}" \
  "https://api.github.com/repos/deis/${repo_name}/commits/${commit}")

  commit_pattern='[a-f0-9]\{40\}'
  echo "${resp}" \
    | jq '.commit.message' \
    | grep -o "\(Merge\)\s${commit_pattern}\s\(into\)" \
    | grep -o "${commit_pattern}"
}
