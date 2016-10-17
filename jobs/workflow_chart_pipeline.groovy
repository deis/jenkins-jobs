def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

def repo = repos.find{ it.name == 'workflow' }
def chart = repo.chart
def chartRepo = [staging: "${chart}-dev", production: chart]

job("${chartRepo.staging}-chart-publish") {
  description "Publishes a release candidate Workflow chart to the ${chartRepo.staging} charts repo."

  scm {
    git {
      remote {
        github("deis/${repo.name}")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('master')
    }
  }

  publishers {
    wsCleanup() // Scrub workspace clean after build

    postBuildScripts {
      onlyIfBuildSucceeds(false)
      steps {
        defaults.statusesToNotify.each { buildStatus ->
          conditionalSteps {
            condition {
             status(buildStatus, buildStatus)
              steps {
                shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                  "slack-notify '${repo.slackChannel}' '${buildStatus}'"
              }
            }
          }
        }
      }
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  parameters {
    stringParam('RELEASE_TAG', defaults.workflow.release, 'Release tag')
    stringParam('HELM_VERSION', 'v2.0.0-alpha.5', 'Version of Helm to download/use')
  }

  wrappers {
    buildName('${RELEASE_TAG} #${BUILD_NUMBER}')
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("AWS_ACCESS_KEY_ID", '57e64439-4521-4a4f-9315-eac10ecdea75')
      string("AWS_SECRET_ACCESS_KEY", '313da896-1579-41fa-9c70-c6b13d938e9c')
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
  }

  steps {
    def components = repos.collectMany {
      it.workflowComponent ? [it.chart] : [] }.join(' ') as String

    shell new File("${workspace}/bash/scripts/get_latest_component_release.sh").text +
      """
        set -x

        if [ -d charts ]; then
          cd charts
          ${defaults.helm.downloadAndInit}

          ## change chart values
          # update the chart version to RELEASE_TAG
          perl -i -0pe "s/<Will be populated by the ci before publishing the chart>/\${RELEASE_TAG}/g" ${chart}/Chart.yaml

          ## update requirements.yaml with latest tags for each component
          for component in ${components}; do
            latest_tag="\$(get-latest-component-release "\${component}")"
            perl -i -0pe "s/<\${component}-tag>/\${latest_tag}/g" ${chart}/requirements.yaml
            helm repo add "\${component}" "https://charts.deis.com/\${component}"
          done

          # create charts dir beforehand (helm issue)
          mkdir ${chart}/charts

          # fetch all dependent charts based on above
          helm dependency update ${chart}

          # DEBUG:
          cat ${chart}/Chart.yaml
          cat ${chart}/values.yaml

          # package release chart
          helm package ${chart}

          # download index file from aws s3 bucket
          aws s3 cp s3://helm-charts/${chartRepo.staging}/index.yaml .

          # update index file
          helm repo index . --url https://charts.deis.com/${chartRepo.staging}

          # push packaged chart and updated index file to aws s3 bucket
          aws s3 cp ${chart}-\${RELEASE_TAG}.tgz s3://helm-charts/${chartRepo.staging}/ \
            && aws s3 cp index.yaml s3://helm-charts/${chartRepo.staging}/index.yaml
        else
          echo "No 'charts' directory found at project level; nothing to publish."
        fi
      """.stripIndent().trim()

    conditionalSteps {
      condition {
        status('SUCCESS', 'SUCCESS')
      }
      steps {
        downstreamParameterized {
          trigger("${repo.name}-dev-chart-e2e") {
            block {
              buildStepFailure('FAILURE')
              failure('FAILURE')
              unstable('UNSTABLE')
            }
            parameters {
              predefinedProps([
                'WORKFLOW_TAG': '${RELEASE_TAG}',
                'HELM_VERSION': '${HELM_VERSION}',
              ])
            }
          }
        }
      }
    }
  }
}

job("${chartRepo.staging}-chart-e2e") {
  description "Runs e2e against candidate release candidate chart from ${chartRepo.staging} chart repo"

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

    postBuildScripts {
      onlyIfBuildSucceeds(false)
      steps {
        defaults.statusesToNotify.each { buildStatus ->
          conditionalSteps {
            condition {
              status(buildStatus, buildStatus)
              steps {
                shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                  "slack-notify '${repo.slackChannel}' '${buildStatus}'"
              }
            }
          }
        }
      }
    }
  }

  parameters {
    stringParam('WORKFLOW_TAG', defaults.workflow.release, 'Workflow chart docker tag')
    stringParam('WORKFLOW_E2E_TAG', '', 'Workflow-E2E chart docker tag')
    stringParam('HELM_VERSION', 'v2.0.0-alpha.5', 'Version of Helm to download/use')
    stringParam('USE_KUBERNETES_HELM', '1', 'Flag to use kubernetes/helm (Default: yes; leave empty for no)')
    stringParam('GINKGO_NODES', '15', "Number of parallel executors to use when running e2e tests")
    stringParam('E2E_RUNNER_IMAGE', 'quay.io/deisci/e2e-runner:canary', "The e2e-runner image")
    stringParam('E2E_DIR', '/home/jenkins/workspace/$JOB_NAME/$BUILD_NUMBER', "Directory for storing workspace files")
    stringParam('E2E_DIR_LOGS', '${E2E_DIR}/logs', "Directory for storing logs. This directory is mounted into the e2e-runner container")
    stringParam('CLUSTER_REGEX', '', 'K8s cluster regex (name) to supply when requesting cluster')
    stringParam('CLUSTER_VERSION', '', 'K8s cluster version to supply when requesting cluster')
  }

  wrappers {
    buildName('${WORKFLOW_TAG} #${BUILD_NUMBER}')
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
    shell new File("${workspace}/bash/scripts/get_latest_component_release.sh").text +
      """
        export WORKFLOW_E2E_TAG="\$(get-latest-component-release workflow-e2e)"
        ${e2eRunnerJob}
      """.stripIndent().trim()
  }
}

job("${chartRepo.production}-chart-publish") {
  description "Publishes official Workflow chart by copying e2e-approved chart from the `${chartRepo.staging}` repo to the `${chartRepo.production}` repo."

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    wsCleanup() // Scrub workspace clean after build

    postBuildScripts {
      onlyIfBuildSucceeds(false)
      steps {
        defaults.statusesToNotify.each { buildStatus ->
          conditionalSteps {
            condition {
              status(buildStatus, buildStatus)
              steps {
                shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                  "slack-notify '${repo.slackChannel}' '${buildStatus}'"
              }
            }
          }
        }
      }
    }
  }

  parameters {
    stringParam('RELEASE_TAG', defaults.workflow.release, 'Release tag')
    stringParam('HELM_VERSION', 'v2.0.0-alpha.5', 'Version of Helm to download/use')
  }

  wrappers {
    buildName('${RELEASE_TAG} #${BUILD_NUMBER}')
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("AWS_ACCESS_KEY_ID", '57e64439-4521-4a4f-9315-eac10ecdea75')
      string("AWS_SECRET_ACCESS_KEY", '313da896-1579-41fa-9c70-c6b13d938e9c')
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
  }

  steps {
    shell """
      #!/usr/bin/env bash

      set -eo pipefail

      ${defaults.helm.downloadAndInit}

      # download index file from aws s3 bucket
      aws s3 cp s3://helm-charts/${chartRepo.production}/index.yaml .
      aws s3 cp s3://helm-charts/${chartRepo.staging}/${chart}-\${RELEASE_TAG}.tgz .

      # update index file
      helm repo index . --url https://charts.deis.com/${chartRepo.production}

      # push packaged chart and updated index file to aws s3 bucket
      aws s3 cp ${chart}-\${RELEASE_TAG}.tgz s3://helm-charts/${chartRepo.production}/ \
        && aws s3 cp index.yaml s3://helm-charts/${chartRepo.production}/index.yaml
    """.stripIndent().trim()
  }
}
