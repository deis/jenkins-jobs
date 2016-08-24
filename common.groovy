evaluate(new File("${WORKSPACE}/repo.groovy"))

WORKFLOW_RELEASE = 'v2.4.2'
TEST_JOB_ROOT_NAME = 'workflow-test'

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

e2eRunnerJob = new File("${WORKSPACE}/bash/scripts/run_e2e.sh").text +
  "run-e2e ${defaults.envFile}"
