def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/repo.groovy"))


defaults = [
  // default, ubuntu-based jenkins nodes
  nodes: ['node1-ec2', 'node2-ec2', 'node3-ec2', 'node4-ec2', 'node8-kubernetes'],
  signingNode: ['node7-ec2'],
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
    remoteRepo: 'https://github.com/deis/charts.git', // helmc-remove
    remoteBranch: 'master', // helmc-remove
    remoteName: 'deis', // helmc-remove
    version: 'v2.0.0',
    useClassic: false, // helmc-remove
  ],
  github: [
    username: 'deis-admin',
    credentialsID: '8e11254f-44f3-4ddd-bf98-2cabcb7434cd',
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
