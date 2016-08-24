#!/usr/bin/env bash
set -eo pipefail

setup-helmc-env() {
  if [ -n "${CHARTS_SHA}" ]; then
    helmc_remote_repo="$(get-remote-repo-url charts "${CHARTS_SHA}")"
    info="\
      Found CHARTS_SHA='${CHARTS_SHA}'
      Exporting the following env vars for setting up the local helmc environment:
        HELM_REMOTE_REPO=${helmc_remote_repo}
        WORKFLOW_BRANCH=${CHARTS_SHA}
        WORKFLOW_E2E_BRANCH=${CHARTS_SHA}
      "
    log-info "${info}"

    { echo HELM_REMOTE_REPO="${helmc_remote_repo}"; \
      echo WORKFLOW_BRANCH="${CHARTS_SHA}"; \
      echo WORKFLOW_E2E_BRANCH="${CHARTS_SHA}"; }
  fi
}

get-remote-repo-url() {
  repo_name="${1}"
  git_commit="${2}"

  # retrieve git commit info
  commit_info=$(curl \
  -fsSL \
  --user deis-admin:"${GITHUB_ACCESS_TOKEN}" \
  "https://api.github.com/repos/deis/${repo_name}/commits/${git_commit}")

  committer_name="$(echo "${commit_info}" | docker run -i --rm kamermans/jq '.commit.committer.name')"

  if [ "${committer_name//\"}" == "GitHub" ]; then
    # commit has presumably been merged
    echo "https://github.com/deis/${repo_name}.git"
  else
    # determine url to retrieve list of committer repos from commit info
    committer_repos_url="$(echo "${commit_info}" | docker run -i --rm kamermans/jq '.committer.repos_url')"

    # retrieve list of committer repos
    committer_all_repos=$(curl \
    -fsSL \
    --user deis-admin:"${GITHUB_ACCESS_TOKEN}" \
    "${committer_repos_url//\"}")

    # determine ${repo_name} clone url from list of committer repos
    clone_url="$(echo "${committer_all_repos}" | docker run -i --rm kamermans/jq '.[].clone_url' | grep "${repo_name}")"

    # return clone url with double-quotes stripped
    echo "${clone_url//\"}"
  fi
}

log-info() {
  local info="${1}"

  if [ -n "${JENKINS_HOME}" ]; then
    echo "${info}" >&2
  fi
}
