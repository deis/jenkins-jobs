def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

def repo = repos.find{ it.name == 'workflow' }
def chart = repo.chart
def bucketNames = [
  builder: "ci-workflow-upgrade-builder-\${BUILD_NUMBER}",
  database: "ci-workflow-upgrade-database-\${BUILD_NUMBER}",
  registry: "ci-workflow-upgrade-registry-\${BUILD_NUMBER}",
]

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
                  "slack-notify '#release' ${buildStatus}"
              }
            }
          }
        }

        // Clean up buckets created during test
        shell new File("${workspace}/bash/scripts/off_cluster_storage.sh").text +
          "cleanup \"${bucketNames.builder} ${bucketNames.database} ${bucketNames.registry}\""
      }
    }
  }

  parameters {
    stringParam('CLOUD_PROVIDER', defaults.e2eRunner.provider)
    stringParam('WORKFLOW_TAG', '', 'Workflow chart version to install (default: empty, will pull latest from given chart repo)')
    stringParam('WORKFLOW_E2E_TAG', '', 'Workflow-E2E chart version (default: empty, will pull latest from given chart repo)')
    stringParam('ORIGIN_WORKFLOW_REPO', 'workflow', 'Workflow chart repo to use for installing')
    stringParam('UPGRADE_WORKFLOW_REPO', 'workflow-staging', 'Workflow chart repo to use for upgrading')
    choiceParam('STORAGE_TYPE', ['', 'gcs', 's3'], "Storage backend for Workflow cluster, default is empty/on-cluster")
    stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
    booleanParam('RUN_E2E', false, "Set to run e2e tests post-upgrade (Default: false)")
    stringParam('GINKGO_NODES', '15', "Number of parallel executors to use when running e2e tests")
    stringParam('E2E_RUNNER_IMAGE', 'quay.io/deisci/e2e-runner:canary', "The e2e-runner image")
    stringParam('E2E_DIR', '/home/jenkins/workspace/$JOB_NAME/$BUILD_NUMBER', "Directory for storing workspace files")
    stringParam('E2E_DIR_LOGS', '${E2E_DIR}/logs', "Directory for storing logs. This directory is mounted into the e2e-runner container")
  }

  wrappers {
    buildName('${STORAGE_TYPE} #${BUILD_NUMBER}')
    timeout {
      absolute(defaults.testJob["timeoutMins"])
      failBuild()
    }
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      // For slack notification
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
      // k8s-claimer auth
      string("AUTH_TOKEN", "a62d7fe9-5b74-47e3-9aa5-2458ba32da52")
      string("CLOUD_PROVIDER", defaults.e2eRunner.provider)

      // Off-cluster storage support
      // gcloud/gsutil
      string("GCS_KEY_JSON", "71dd890b-e4ce-4483-97ca-a7c286c2381c")
      // Workflow consumes:
      string("AWS_SECRET_KEY","033f76e3-21b8-4bae-a7b3-7f43e07138d3")
      string("AWS_ACCESS_KEY","02a5c84e-3248-4776-acc5-6b6a1fb24dfc")
      // aws cli consumes:
      string("AWS_SECRET_ACCESS_KEY","033f76e3-21b8-4bae-a7b3-7f43e07138d3")
      string("AWS_ACCESS_KEY_ID","02a5c84e-3248-4776-acc5-6b6a1fb24dfc")
    }
  }

  steps {
    shell """
      #!/usr/bin/env bash

      set -eo pipefail

      mkdir -p ${defaults.tmpPath}

      if [ "\${STORAGE_TYPE}" != "" ]; then
        { echo REGISTRY_BUCKET="${bucketNames.registry}"; \
          echo BUILDER_BUCKET="${bucketNames.builder}"; \
          echo DATABASE_BUCKET="${bucketNames.database}"; } >> ${defaults.envFile}
      fi
    """.stripIndent().trim()

    shell "${e2eRunnerJob} './run.sh upgrade'"
  }
}
