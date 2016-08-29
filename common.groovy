evaluate(new File("${WORKSPACE}/repo.groovy"))

WORKFLOW_RELEASE = 'v2.4.2'
TEST_JOB_ROOT_NAME = 'workflow-test'

E2E_RUNNER_JOB = '''#!/usr/bin/env bash
set -eo pipefail

export WORKFLOW_CHART="workflow-${RELEASE}"
export WORKFLOW_E2E_CHART="workflow-${RELEASE}-e2e"

export CLI_VERSION="${CLI_VERSION:-latest}"
if [ -n "${WORKFLOW_CLI_SHA}" ]; then
  export CLI_VERSION="${WORKFLOW_CLI_SHA:0:7}"
fi

mkdir -p ${E2E_DIR_LOGS}
env > ${E2E_DIR}/env.file
if [ -e "/tmp/${JOB_NAME}/${BUILD_NUMBER}/env.properties" ]; then
  cat /tmp/${JOB_NAME}/${BUILD_NUMBER}/env.properties >> ${E2E_DIR}/env.file
fi
docker pull ${E2E_RUNNER_IMAGE} # bust the cache as tag may be canary
docker run -u jenkins:jenkins --env-file=${E2E_DIR}/env.file -v ${E2E_DIR_LOGS}:/home/jenkins/logs:rw $E2E_RUNNER_IMAGE
'''.stripIndent()

defaults = [
  tmpPath: '/tmp/${JOB_NAME}/${BUILD_NUMBER}',
  envFile: '/tmp/${JOB_NAME}/${BUILD_NUMBER}/env.properties',
  daysToKeep: 14,
  testJob: [
    master: "${TEST_JOB_ROOT_NAME}",
    pr: "${TEST_JOB_ROOT_NAME}-pr",
    release: "${TEST_JOB_ROOT_NAME}-release",
    reportMsg: "Test Report: ${JENKINS_URL}job/\${JOB_NAME}/\${BUILD_NUMBER}/testReport",
    timeoutMins: 30,
  ],
  maxBuildsPerNode: 1,
  maxTotalConcurrentBuilds: 3,
  maxWorkflowTestConcurrentBuilds: 3,
  maxWorkflowTestPRConcurrentBuilds: 13,
  maxWorkflowReleaseConcurrentBuilds: 1,
  workflow: [
    chartName: 'workflow-dev',
    release: "${WORKFLOW_RELEASE}",
  ],
  slack: [
    teamDomain: 'deis',
    channel: '#testing',
  ],
  helm: [
    remoteRepo: 'https://github.com/deis/charts.git',
    remoteBranch: 'master',
    remoteName: 'deis',
  ],
  github: [
    username: 'deis-admin',
    credentialsID: '8e11254f-44f3-4ddd-bf98-2cabcb7434cd',
  ],
]
