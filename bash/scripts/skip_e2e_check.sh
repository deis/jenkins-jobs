#!/usr/bin/env bash
set -eo pipefail

main() {
  check-skip-e2e "${ghprbPullLongDescription}"
}

# check-skip-e2e checks if 'skip e2e' is provided in commit body
check-skip-e2e() {
  description="${1}"

  skipE2e=`echo "${description}" | grep -o "skip e2e"` || true

  if [ -n "${skipE2e}" ]; then
    echo "'skip e2e' found in commit body so skipping e2e test run"
    exit 1
  fi
}

main
