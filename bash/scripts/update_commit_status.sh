#!/usr/bin/env bash
set -eo pipefail

update-commit-status() {
  commit_status="${1}"
  repo_name="${2}"
  git_commit="${3}"
  target_url="${4}"
  description="${5}"

  if [ -z "${git_commit}" ]; then
    echo "Commit value is empty; cannot update status."
    return 0
  fi

  data='
    {"state":"'"${commit_status}"'",
    "target_url":"'"${target_url}"'",
    "description":"'"${description}"'",
    "context":"ci/jenkins/pr"}
  '

  { echo "Updating commit '${git_commit}' in repo '${repo_name}' with the following data:"; \
    echo "${data}"; } >&2

  curl \
    -sSL \
    --user deis-admin:"${GITHUB_ACCESS_TOKEN}" \
    --data "${data}" \
    "https://api.github.com/repos/deis/${repo_name}/statuses/${git_commit}"
}
