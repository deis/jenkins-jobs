#!/usr/bin/env bash
set -eo pipefail

main() {
  repoName="workflow"

  milestone=$(retrieve-github-milestone "${repoName}")
  currentVersion="${WORKFLOW_VERSION:-$(cat "$(get-workflow-version-filepath)")}"

  if [ "${milestone}" == "" ]; then
    echo "No milestone found from the deis/${repoName} repo!"
  elif [ "${currentVersion}" != "${milestone}" ]; then
    echo "Bumping current Workflow version from '${currentVersion}' to '${milestone}'..."
    bump-workflow-version "${milestone}"
  else
    echo "Current Workflow version '${currentVersion}' already matches '${milestone}'."
  fi
}

retrieve-github-milestone() {
  repoName="${1}"

  curl \
  -sSL \
  --user deis-admin:"${GITHUB_ACCESS_TOKEN}" \
  "https://api.github.com/repos/deis/${repoName}/milestones" \
  | docker run -i --rm kamermans/jq '.[].title' | head -n1
}

bump-workflow-version() {
  version="${1}"

  echo "${version}" > "$(get-workflow-version-filepath)"
  git diff
  # TODO: enable once this job gets a test flight...
  # git commit -a -m "chore(workflow-${version}): update WORKFLOW_RELEASE to ${version}"
  # git push origin master
}

get-workflow-version-filepath() {
  ret="/dev/null"
  if [ -n "${JENKINS_HOME}" ]; then
    ret="${WORKSPACE}/workflow.version"
  fi
  echo "${ret}"
}

main
