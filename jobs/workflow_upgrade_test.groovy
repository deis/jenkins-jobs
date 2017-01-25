def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

def repo = repos.find{ it.name == 'workflow' }
def chart = repo.chart

job("${chart}-upgrade-test") {
  description "Installs the latest Workflow chart from the ORIGIN_WORKFLOW_REPO chart repo " +
    "and runs 'helm upgrade' to the latest chart in the UPGRADE_WORKFLOW_REPO chart repo; reports outcome."

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    archiveJunit('${BUILD_NUMBER}/logs/junit*.xml') {
      retainLongStdout(false)
      allowEmptyResults(true)
    }

    archiveArtifacts {
      pattern('${BUILD_NUMBER}/logs/**')
      onlyIfSuccessful(false)
      fingerprint(false)
      allowEmpty(true)
    }

    def statusesToNotify = ['SUCCESS', 'FAILURE']
    postBuildScripts {
      onlyIfBuildSucceeds(false)
      steps {
        statusesToNotify.each { buildStatus ->
          conditionalSteps {
            condition {
             status(buildStatus, buildStatus)
              steps {
                // Dispatch Slack notification
                shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                  """
                    slack-notify "#release" ${buildStatus}
                  """.stripIndent().trim()
              }
            }
          }
        }
      }
    }
  }

  parameters {
    stringParam('WORKFLOW_TAG', '', 'Workflow chart version to install (default: empty, will pull latest from given chart repo)')
    stringParam('WORKFLOW_E2E_TAG', '', 'Workflow-E2E chart version (default: empty, will pull latest from given chart repo)')
    stringParam('ORIGIN_WORKFLOW_REPO', 'workflow', 'Workflow chart repo to use for installing')
    stringParam('UPGRADE_WORKFLOW_REPO', 'workflow-staging', 'Workflow chart repo to use for upgrading')
    stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
    booleanParam('RUN_E2E', false, "Set to run e2e tests post-upgrade (Default: false)")
    stringParam('GINKGO_NODES', '15', "Number of parallel executors to use when running e2e tests")
    stringParam('E2E_RUNNER_IMAGE', 'quay.io/deisci/e2e-runner:canary', "The e2e-runner image")
    stringParam('E2E_DIR', '/home/jenkins/workspace/$JOB_NAME/$BUILD_NUMBER', "Directory for storing workspace files")
    stringParam('E2E_DIR_LOGS', '${E2E_DIR}/logs', "Directory for storing logs. This directory is mounted into the e2e-runner container")
  }

  wrappers {
    timeout {
      absolute(defaults.testJob["timeoutMins"])
      failBuild()
    }
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("AUTH_TOKEN", "a62d7fe9-5b74-47e3-9aa5-2458ba32da52")
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
  }

  steps {
    shell "${e2eRunnerJob} upgrade"
  }
}
