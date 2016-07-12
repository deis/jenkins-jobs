#!/usr/bin/env bash

set -eo pipefail

# Gets actual commit for reporting status and potentially tagging Docker image
main() {
  envPropsFilepath="/dev/null"
  if [ -n "${JENKINS_HOME}" ]; then
    envPropsFilepath="${WORKSPACE}/env.properties"
  fi

  export ACTUAL_COMMIT="${sha1}"
  # if triggered by pull request plugin, use ghprbActualCommit
  if [ "${ghprbActualCommit}" != "" ]; then
    export ACTUAL_COMMIT="${ghprbActualCommit}"
    export VERSION="git-${ACTUAL_COMMIT:0:7}"
    echo "PR build, so using VERSION=${VERSION} for Docker image tag rather than the merge commit"
  fi
  echo ACTUAL_COMMIT="${ACTUAL_COMMIT}" > "${envPropsFilepath}"
}

main
