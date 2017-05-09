def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/repo.groovy"))

defaults = [
  signingNode: ['deis-signatory'],
  tmpPath: '/tmp/${JOB_NAME}/${BUILD_NUMBER}',
  envFile: '/tmp/${JOB_NAME}/${BUILD_NUMBER}/env.properties',
  daysToKeep: 14,
  testJob: [
    name: 'workflow-chart-e2e',
    timeoutMins: 30,
  ],
  maxBuildsPerNode: 1,
  maxTotalConcurrentBuilds: 3,
  maxWorkflowTestConcurrentBuilds: 5,
  cli: [
    release: 'stable',
  ],
  slack: [
    teamDomain: 'deis',
    channel: '#testing',
    webhookURL: 'a53b3a9e-d649-4cff-9997-6c24f07743c8',
  ],
  helm: [
    version: 'v2.3.1',
  ],
  github: [
    username: 'deis-admin',
    credentialsID: '393d5b89-9da5-410a-a476-94c9f4325217',
  ],
  azure: [
    storageAccount: 'f2a28186-f3e1-4a51-9d15-e1646bdf34e6',
    storageAccountKeyID: 'c9cad0b0-53dd-4d38-a832-c4b29aeaf49b',
  ],
  statusesToNotify: ['SUCCESS', 'FAILURE'],
]

e2eRunnerJob = new File("${workspace}/bash/scripts/run_e2e.sh").text +
  "run-e2e ${defaults.envFile}"

checkForChartChanges = new File("${workspace}/bash/scripts/get_merge_commit_changes.sh").text +
  '''
    changes="$(get-merge-commit-changes "$(git rev-parse --short HEAD)")"
    echo "${changes}" | grep 'charts/'
  '''.stripIndent().trim()
