repos = [
  [name: 'builder', slackChannel: 'builder'],
  [name: 'dockerbuilder', slackChannel: 'builder'],
  [name: 'fluentd', slackChannel: 'logger'],
  [name: 'logger', slackChannel: 'logger'],
  [name: 'minio', slackChannel: 'object-store'],
  [name: 'postgres', slackChannel: 'postgres'],
  [name: 'registry', slackChannel: 'registry'],
  [name: 'router', slackChannel: 'router'],
  [name: 'slugbuilder', slackChannel: 'builder'],
  [name: 'slugrunner', slackChannel: 'builder'],
  [name: 'stdout-metrics', slackChannel: 'metrics'],
  [name: 'controller', slackChannel: 'controller'],
  [name: 'workflow-e2e', slackChannel: 'testing'],
  [name: 'workflow-manager', slackChannel: 'wfm'],
]

repos.each { Map repo ->
  repo.commitEnvVar = "${repo.name.toUpperCase().replaceAll('-', '_')}_SHA"
}

TEST_JOB_ROOT_NAME = 'workflow-test'

defaults = [
  daysToKeep: 14,
  bumpverCommitCmd: 'git commit -a -m "chore(versions): ci bumped versions via ${BUILD_URL}" || true',
  testJob: [
    master: "${TEST_JOB_ROOT_NAME}",
    pr: "${TEST_JOB_ROOT_NAME}-pr",
    reportMsg: "Test Report: ${JENKINS_URL}job/\${JOB_NAME}/\${BUILD_NUMBER}/testReport",
    // Revisit when https://github.com/deis/jenkins-jobs/issues/51 complete
    // (timeout can/should be decreased)
    timeoutMins: 30,
  ],
  maxBuildsPerNode: 1,
  maxTotalConcurrentBuilds: 3,
  workflowChart: 'workflow-dev',
  workflowRelease: 'dev',
  slack: [
    teamDomain: 'deis',
    channel: '#testing',
  ],
  helm: [
    remoteRepo: 'https://github.com/deis/charts.git',
    remoteBranch: 'master',
    remoteName: 'deis',
  ],
]
