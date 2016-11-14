#!/usr/bin/env bash

set -eo pipefail

get-merge-commit-changes() {
  merge_commit="${1}"

  # Grab 'Merge: abc1234 def5678' and convert to abc1234..def5678
  child_commit_range="$(git show "${merge_commit}" | grep 'Merge:' | cut -c8- | sed 's/ /../g')"

  echo "Returning changes from merge commit '${merge_commit}' using the commit range: ${child_commit_range}" >&2

  git diff-tree --no-commit-id --name-only -r "${child_commit_range}"
}
