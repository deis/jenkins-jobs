#!/usr/bin/env bash
set -eo pipefail

main() {
  envPropsFilepath="/dev/null"
  if [ -n "${JENKINS_HOME}" ]; then
    envPropsFilepath="${WORKSPACE}/env.properties"
  fi
  parse-commit-description "${ghprbPullLongDescription}" >> "${envPropsFilepath}"
}

# parse-commit-description parses a commit description for any required sibling
# commits to pass along to downstream job(s)...
parse-commit-description() {
  description="${1}"

  # Looks specifically for matches of '[rR]equires <repo>#<prNumber>',
  # e.g., "requires builder#123, Requires router#567"
  reqs=$(echo "${description}" | grep -o "[Rr]equires [-a-z]*#[0-9]\{1,9\}" | grep -o "[-a-z]*#[0-9]\{1,9\}") || true

  # split on whitespace into array of '<repo>#<prNumber>' values
  reqsArray=(${reqs// / })

  # for each '<repo>#<prNumber>' value in array
  local i
  for i in "${reqsArray[@]}"; do
    # split on '#'
    repoPRNumberArray=(${i//#/ })
    repoName="${repoPRNumberArray[0]}"
    prNumber="${repoPRNumberArray[1]}"

    # curl GH api for needed commit sha, providing name and issue number
    sha=$(get-most-recent-pr-commit "${repoName}" "${prNumber}")

    if [[ ${sha} =~ ^[a-f0-9]{5,40}$ ]]; then
      # echo '<uppercased repo>_SHA'=<prNumber> (with hyphens converted to underscores)
      repoEnvVar=$(echo "${repoName//-/_}" | awk '{print toupper($0)}')_SHA
      echo "${repoEnvVar}"="${sha}"
    else
      echo "Commit sha for PR #${prNumber} in repo '${repoName}' not found."
      return 1
    fi
  done
}

get-most-recent-pr-commit() {
  repoName="${1}"
  prNumber="${2}"

  curl \
  -sSL \
  --user deis-admin:"\${GITHUB_ACCESS_TOKEN}" \
  "https://api.github.com/repos/deis/${repoName}/pulls/${prNumber}/commits" \
  | docker run -i --rm kamermans/jq '.[].sha' | tail -n1
}

main
