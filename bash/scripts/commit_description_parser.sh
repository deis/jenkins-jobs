#!/usr/bin/env bash
set -eo pipefail

main() {
  envPropsFilepath="/dev/null"
  if [ -n "${JENKINS_HOME}" ] && [ "${JOB_BASE_NAME}" != "jenkins-jobs" ]; then
    envPropsFilepath="${WORKSPACE}/env.properties"
  fi
  parse-commit-description "${ghprbPullLongDescription}" >> "${envPropsFilepath}"
}

# parse-commit-description parses a commit description for any required sibling
# to pass along to downstream job(s)...
parse-commit-description() {
  description="${1}"

  # Looks specifically for matches of '[rR]equires <repo>#<sha>',
  # e.g., "requires builder#abc1234, Requires router#def5678"
  reqs=`echo "${description}" | grep -o "[Rr]equires [-a-z]*#[a-z0-9]*" | grep -o "[-a-z]*#[a-z0-9]*"` || true

  # split on whitespace into array of '<repo>#<sha>' values
  reqsArray=(${reqs// / })

  # for each '<repo>#<sha>' value in array
  for i in "${reqsArray[@]}"; do
    # split on '#'
    repoShaArray=(${i//#/ })
    # echo '<uppercased repo>_SHA'=<sha> (with hyphens converted to underscores)
    repoEnvVar=`echo "${repoShaArray[0]//-/_}" | awk '{print toupper($0)}'`_SHA
    echo "${repoEnvVar}"="${repoShaArray[1]}"
  done
}

main
