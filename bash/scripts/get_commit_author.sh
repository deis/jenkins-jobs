#!/usr/bin/env bash

set -eo pipefail

main() {
  echo COMMIT_AUTHOR="\"$(git rev-parse HEAD | git --no-pager show -s --format='%an')\"" \
    >> "${ENV_PROPS_FILEPATH:-${WORKSPACE}/env.properties}"
}

if [ -n "${JENKINS_HOME}" ]; then
  main
fi
