def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/repo.groovy"))

def workflowChartRelease = 'v2.8.0'
def testJobRootName = 'workflow-test'

defaults = [
  tmpPath: '/tmp/${JOB_NAME}/${BUILD_NUMBER}',
  envFile: '/tmp/${JOB_NAME}/${BUILD_NUMBER}/env.properties',
  daysToKeep: 14,
  testJob: [
    master: testJobRootName,
    pr: "${testJobRootName}-pr",
    release: "${testJobRootName}-release",
    timeoutMins: 30,
  ],
  maxBuildsPerNode: 1,
  maxTotalConcurrentBuilds: 3,
  maxWorkflowTestConcurrentBuilds: 3,
  maxWorkflowTestPRConcurrentBuilds: 13,
  maxWorkflowReleaseConcurrentBuilds: 1,
  workflow: [
    chartName: 'workflow-dev',
    release: workflowChartRelease,
  ],
  cli: [
    release: 'stable',
  ],
  slack: [
    teamDomain: 'deis',
    channel: '#testing',
    webhookURL: 'a53b3a9e-d649-4cff-9997-6c24f07743c8',
  ],
  helm: [
    remoteRepo: 'https://github.com/deis/charts.git',
    remoteBranch: 'master',
    remoteName: 'deis',
    version: 'v2.0.0-rc.2',
  ],
  github: [
    username: 'deis-admin',
    credentialsID: '8e11254f-44f3-4ddd-bf98-2cabcb7434cd',
  ],
  statusesToNotify: ['SUCCESS', 'FAILURE'],
]

e2eRunnerJob = new File("${workspace}/bash/scripts/run_e2e.sh").text +
  "run-e2e ${defaults.envFile}"
