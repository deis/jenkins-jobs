repos = [
  [ name: 'builder',
    components: [[name: 'builder']],
    slackChannel: '#builder',
    runE2e: true,
    workflowComponent: true,
    chart: 'builder'],

  [ name: 'charts',
    buildJobs: false],

  [ name: 'controller',
    components: [[name: 'controller']],
    slackChannel: '#controller',
    runE2e: true,
    workflowComponent: true,
    chart: 'controller'],

  [ name: 'dockerbuilder',
    components: [[name: 'dockerbuilder']],
    slackChannel: '#builder',
    runE2e: true,
    workflowComponent: true,
    chart: 'dockerbuilder'],

  [ name: 'e2e-runner',
    components: [[name: 'e2e-runner']],
    slackChannel: '#testing',
    runE2e: true,
    workflowComponent: false],

  [ name: 'fluentd',
    components: [[name: 'fluentd']],
    slackChannel: '#logger',
    runE2e: true,
    workflowComponent: true,
    chart: 'fluentd'],

  [ name: 'jenkins',
    components: [[name: 'jenkins']],
    slackChannel: '#testing',
    chart: 'jenkins'],

  [ name: 'jenkins-node',
    components: [[name: 'jenkins-node']],
    slackChannel: '#testing',
    chart: 'jenkins-node'],

  [ name: 'logger',
    components: [[name: 'logger']],
    slackChannel: '#logger',
    runE2e: true,
    workflowComponent: true,
    chart: 'logger'],

  [ name: 'minio',
    components: [[name: 'minio']],
    slackChannel: '#object-store',
    runE2e: true,
    workflowComponent: true,
    chart: 'minio'],

  [ name: 'monitor',
    components: [[name: 'grafana'], [name: 'telegraf']],
    slackChannel: '#monitor',
    runE2e: false,
    workflowComponent: true,
    chart: 'monitor'],

  [ name: 'nsq',
    components: [[name: 'nsq']],
    slackChannel: '#logger',
    runE2e: true,
    workflowComponent: true,
    chart: 'nsqd'],

  [ name: 'postgres',
    components: [[name: 'postgres']],
    slackChannel: '#postgres',
    runE2e: true,
    workflowComponent: true,
    chart: 'database'],

  [ name: 'redis',
    components: [[name: 'redis']],
    slackChannel: '#logger',
    runE2e: true,
    workflowComponent: true,
    chart: 'redis'],

  [ name: 'registry',
    components: [[name: 'registry']],
    slackChannel: '#registry',
    runE2e: true,
    workflowComponent: true,
    chart: 'registry'],

  [ name: 'registry-token-refresher',
    components: [[name: 'registry-token-refresher']],
    slackChannel: '#registry',
    runE2e: false,
    workflowComponent: true,
    chart: 'registry-token-refresher'],

  [ name: 'router',
    components: [[name: 'router']],
    slackChannel: '#router',
    runE2e: true,
    workflowComponent: true,
    chart: 'router'],

  [ name: 'slugbuilder',
    components: [[name: 'slugbuilder']],
    slackChannel: '#builder',
    runE2e: true,
    workflowComponent: true,
    chart: 'slugbuilder'],

  [ name: 'slugrunner',
    components: [[name: 'slugrunner']],
    slackChannel: '#builder',
    runE2e: true,
    workflowComponent: true,
    chart: 'slugrunner'],

  [ name: 'workflow',
    buildJobs: false,
    slackChannel: '#workflow',
    chart: 'workflow'],

  [ name: 'workflow-cli',
    buildJobs: false,
    slackChannel: '#controller'],

  [ name: 'workflow-e2e',
    components: [[name: 'workflow-e2e']],
    slackChannel: '#testing',
    runE2e: true,
    chart: 'workflow-e2e'],

  [ name: 'workflow-manager',
    components: [[name: 'workflow-manager']],
    slackChannel: '#wfm',
    runE2e: false,
    workflowComponent: true,
    chart: 'workflow-manager'],
]

repos.each { Map repo ->
  repo.commitEnvVar = "${repo.name.toUpperCase().replaceAll('-', '_')}_SHA"

  repo.components.each { Map component ->
    component.envFile = "/tmp/\${JOB_NAME}/\${BUILD_NUMBER}/${component.name}/env.properties"
  }
}
