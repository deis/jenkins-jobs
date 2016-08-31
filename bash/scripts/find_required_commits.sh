#!/usr/bin/env bash
set -eo pipefail

# find-required-commits parses a commit description for any required sibling
# commits to pass along to downstream job(s)...
find-required-commits() {
  git_commit="${1}"
  commit_description="${COMMIT_DESCRIPTION:-$(git log --format=%B -n 1 "${git_commit}")}"

  # Looks specifically for matches of '[rR]equires <repo>#<pr_number>',
  # e.g., "requires builder#123, Requires router#567"
  reqs=$(echo "${commit_description}" | grep -o "[Rr]equires \(deis\/\)\?[-a-z0-9]*#[0-9]\{1,9\}" | grep -o "[-a-z0-9]*#[0-9]\{1,9\}") || true

  if [ "${reqs}" != "" ]; then
    # split on whitespace into array of '<repo>#<pr_number>' values
    reqsArray=(${reqs// / })

    # for each '<repo>#<pr_number>' value in array
    local i
    for i in "${reqsArray[@]}"; do
      # split on '#'
      repo_pr_number_ary=(${i//#/ })
      repo_name="${repo_pr_number_ary[0]}"
      pr_number="${repo_pr_number_ary[1]}"

      # get all commits from pr_number in repo_name, filtering for most recent, stripping quotes
      sha=$(get-pr-commits "${repo_name}" "${pr_number}" | tail -n1 | tr -d '"')

      if [[ ${sha} =~ ^[a-f0-9]{5,40}$ ]]; then
        # echo '<uppercased repo>_SHA'=<pr_number> (with hyphens converted to underscores)
        repoEnvVar=$(echo "${repo_name//-/_}" | awk '{print toupper($0)}')_SHA
        echo "${repoEnvVar}"="${sha}"
      else
        echo "Failure: Commit sha for PR #${pr_number} in repo '${repo_name}' not found!" 1>&2
        exit 1
      fi
    done
  fi
}

get-pr-commits() {
  repo_name="${1}"
  pr_number="${2}"

  resp=$(curl \
  -sSL \
  --user deis-admin:"${GITHUB_ACCESS_TOKEN}" \
  "https://api.github.com/repos/deis/${repo_name}/pulls/${pr_number}/commits")

  echo "${resp}" >&2
  echo "${resp}" | jq '.[].sha'
}
