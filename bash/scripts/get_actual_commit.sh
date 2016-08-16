#!/usr/bin/env bash

set -eo pipefail

get-actual-commit() {
  git_branch="${GIT_BRANCH:-$(git describe --all)}"
  git_commit="${GIT_COMMIT:-$(git rev-parse HEAD)}"

  if [[ "${git_branch}" != *"master"* ]]; then
    # Determine actual PR commit for reporting status and Docker image tagging
    git_commit_parents="${GIT_COMMIT_PARENTS:-$(echo "${git_commit}" | git log --pretty=%P -n 1 --date-order)}"

    if [ "${#git_commit_parents}" -gt 40 ]; then
      # More than one merge commit parent signifies that the merge commit is not the PR commit
      git_commit="${git_commit_parents:0:40}"
    # else only one merge commit parent signifies that the merge commit is also the PR commit
    fi
  fi
  echo "${git_commit}"
}
