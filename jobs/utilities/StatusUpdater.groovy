package utilities

class StatusUpdater {
  static updateStatus(Map args) {
"""#!/usr/bin/env bash
set -eo pipefail

curl \
--silent \
--user deis-admin:"\${GITHUB_ACCESS_TOKEN}" \
--data '{ \
"state":"${args.commitStatus}", \
"target_url":"'"\${BUILD_URL}"'", \
"description":"${args.description}", \
"context":"ci/jenkins/pr"}' \
"https://api.github.com/repos/deis/${args.repoName}/statuses/${args.commitSHA}"
""".stripIndent()
  }
}
