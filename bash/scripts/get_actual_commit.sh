#!/usr/bin/env bash

set -eo pipefail

get-actual-commit() {
  repo_name="${1}"
  git_branch="${GIT_BRANCH:-$(git describe --all)}"
  git_commit="${GIT_COMMIT:-$(git rev-parse HEAD)}"

  if [[ "${git_branch}" != *"master"* ]]; then
    pr_commit="$(get-pr-commit "${repo_name}" "${git_commit}")"
    if [ -n "${pr_commit}" ]; then
      git_commit="${pr_commit}"
    fi
  fi
  echo "${git_commit}"
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

  echo "${resp}" \
    | docker run -i --rm kamermans/jq '.commit.message' \
    | grep -o "\(Merge\)\s[a-f0-9]*\s\(into\)" \
    | grep -o "[a-f0-9]\{40\}"
}
