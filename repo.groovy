repos = [
  [ name: 'builder',
    components: [[name: 'builder']],
    slackChannel: 'builder',
    runE2e: true],

  [ name: 'charts',
    buildJobs: false],

  [ name: 'controller',
    components: [[name: 'controller']],
    slackChannel: 'controller',
    runE2e: true],

  [ name: 'dockerbuilder',
    components: [[name: 'dockerbuilder']],
    slackChannel: 'builder',
    runE2e: true],

  [ name: 'fluentd',
    components: [[name: 'fluentd']],
    slackChannel: 'logger',
    runE2e: true],

  [ name: 'logger',
    components: [[name: 'logger']],
    slackChannel: 'logger',
    runE2e: true],

  [ name: 'minio',
    components: [[name: 'minio']],
    slackChannel: 'object-store',
    runE2e: true],

  [ name: 'monitor',
    components: [[name: 'grafana'], [name: 'influxdb'], [name: 'telegraf']],
    slackChannel: 'monitor',
    runE2e: false],

  [ name: 'nsq',
    components: [[name: 'nsq']],
    slackChannel: 'logger',
    runE2e: true],

  [ name: 'postgres',
    components: [[name: 'postgres']],
    slackChannel: 'postgres',
    runE2e: true],

  [ name: 'redis',
    components: [[name: 'redis']],
    slackChannel: 'logger',
    runE2e: true],

  [ name: 'registry',
    components: [[name: 'registry']],
    slackChannel: 'registry',
    runE2e: true],

  [ name: 'registry-proxy',
    components: [[name: 'registry-proxy']],
    slackChannel: 'registry',
    runE2e: true],

  [ name: 'router',
    components: [[name: 'router']],
    slackChannel: 'router',
    runE2e: true],

  [ name: 'slugbuilder',
    components: [[name: 'slugbuilder']],
    slackChannel: 'builder',
    runE2e: true],

  [ name: 'slugrunner',
    components: [[name: 'slugrunner']],
    slackChannel: 'builder',
    runE2e: true],

  [ name: 'workflow-cli',
    buildJobs: false],

  [ name: 'workflow-e2e',
    components: [[name: 'workflow-e2e']],
    slackChannel: 'testing',
    runE2e: true],

  [ name: 'workflow-manager',
    components: [[name: 'workflow-manager']],
    slackChannel: 'wfm',
    runE2e: false],
]

repos.each { Map repo ->
  repo.commitEnvVar = "${repo.name.toUpperCase().replaceAll('-', '_')}_SHA"

  repo.components.each { Map component ->
    component.envFile = "/tmp/\${JOB_NAME}/\${BUILD_NUMBER}/${component.name}/env.properties"
  }
}
