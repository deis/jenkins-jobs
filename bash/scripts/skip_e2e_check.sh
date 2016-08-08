#!/usr/bin/env bash
set -eo pipefail

main() {
  envPropsFilepath="${ENV_PROPS_FILEPATH:-${WORKSPACE}/env.properties}"
  # read ACTUAL_COMMIT from env props file, cutting/saving only the value
  actual_commit="${ACTUAL_COMMIT:-$(grep ACTUAL_COMMIT "${envPropsFilepath}" | cut -d = -f 2)}"
  # get commit description from commit
  commit_description="${COMMIT_DESCRIPTION:-$(git log --format=%B -n 1 "${actual_commit}")}"
  # parse it and send it on its way
  check-skip-e2e "${commit_description}"
}

# check-skip-e2e checks if 'skip e2e' is provided in commit body
check-skip-e2e() {
  description="${1}"

  skipE2e=$(echo "${description}" | grep -o "skip e2e") || true

  if [ -n "${skipE2e}" ]; then
    echo "'skip e2e' found in commit body so skipping e2e test run"
    exit 1
  fi
}

if [ -n "${JENKINS_HOME}" ]; then
  main
fi
