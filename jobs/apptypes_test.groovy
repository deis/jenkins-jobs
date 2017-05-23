def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

name = "apptypes_e2e"

job(name) {
  description """
    Runs the buildpack and dockerfile sub-suites of e2e tests on a nightly basis
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/e2e-runner")
      }
      branch('master')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  parameters {
    stringParam('RELEASE', "dev", "Release string for resolving workflow-[release](-e2e) charts")
    stringParam('E2E_RUNNER_IMAGE', 'quay.io/deisci/e2e-runner:canary', "The e2e-runner image")
    stringParam('E2E_DIR', '/home/jenkins/workspace/$JOB_NAME/$BUILD_NUMBER', "Directory for storing workspace files")
    stringParam('E2E_DIR_LOGS', '${E2E_DIR}/logs', "Directory for storing logs. This directory is mounted into the e2e-runner container")
    stringParam('CLOUD_PROVIDER', defaults.e2eRunner.provider)
  }

  triggers {
    cron('@daily')
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("AUTH_TOKEN", "a62d7fe9-5b74-47e3-9aa5-2458ba32da52")
    }
  }

  steps {
    shell """
      #!/usr/bin/env bash
      set -eo pipefail

      mkdir -p ${defaults.tmpPath}

      make docker-test

      { echo TEST="bps"; \
        echo COMPONENT_REPO="e2e-runner"; \
        echo UPSTREAM_SLACK_CHANNEL="testing"; } >> ${defaults.envFile}
    """.stripIndent().trim()

    shell e2eRunnerJob
  }
}
