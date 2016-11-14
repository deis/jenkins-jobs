def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

def repo = repos.find{ it.name == 'workflow' }
def chart = repo.chart
def chartRepo = [dev: "${chart}-dev", staging: "${chart}-staging", production: chart]

job("${chart}-chart-publish") {
  description "Publishes a release candidate Workflow chart to the chart repo determined by CHART_REPO_TYPE."

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
    choiceParam('CHART_REPO_TYPE', ['dev', 'staging', 'production'], 'Type of chart repo for publishing (default: dev)')
    stringParam('RELEASE_TAG', '', 'Release tag')
    stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
  }

  wrappers {
    buildName('${RELEASE_TAG} ${CHART_REPO_TYPE} #${BUILD_NUMBER}')
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
      it.workflowComponent ? [it.chart+':'+it.name] : [] }.join(' ') as String

    shell new File("${workspace}/bash/scripts/get_latest_component_release.sh").text +
          new File("${workspace}/bash/scripts/helm_chart_actions.sh").text +
          new File("${workspace}/bash/scripts/publish_helm_chart.sh").text +
      """
        export COMPONENT_CHART_AND_REPOS="${components}"
        export ENV_FILE_PATH="${defaults.envFile}"
        mkdir -p ${defaults.tmpPath}

        publish-helm-chart workflow \${CHART_REPO_TYPE}
      """.stripIndent().trim()

    conditionalSteps {
      condition {
        status('SUCCESS', 'SUCCESS')
      }
      steps {
        downstreamParameterized {
          trigger("${chart}-chart-e2e") {
            block {
              buildStepFailure('FAILURE')
              failure('FAILURE')
              unstable('UNSTABLE')
            }
            parameters {
              propertiesFile(defaults.envFile)
              predefinedProps([
                'CHART_REPO_TYPE': '${CHART_REPO_TYPE}',
                'HELM_VERSION': '${HELM_VERSION}',
              ])
            }
          }
        }
      }
    }
  }
}

job("${chart}-chart-e2e") {
  description "Runs e2e against candidate release candidate chart from chart repo determined by CHART_REPO_TYPE."

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
    stringParam('WORKFLOW_TAG', '', 'Workflow chart docker tag (default: empty, will pull latest)')
    stringParam('WORKFLOW_E2E_TAG', '', 'Workflow-E2E chart docker tag (default: empty, will pull latest)')
    choiceParam('CHART_REPO_TYPE', ['dev', 'staging', 'production'], 'Type of chart repo for publishing (default: dev)')
    stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
    booleanParam('USE_KUBERNETES_HELM', true, 'Flag to use kubernetes/helm (Default: true)')
    stringParam('GINKGO_NODES', '15', "Number of parallel executors to use when running e2e tests")
    stringParam('E2E_RUNNER_IMAGE', 'quay.io/deisci/e2e-runner:canary', "The e2e-runner image")
    stringParam('E2E_DIR', '/home/jenkins/workspace/$JOB_NAME/$BUILD_NUMBER', "Directory for storing workspace files")
    stringParam('E2E_DIR_LOGS', '${E2E_DIR}/logs', "Directory for storing logs. This directory is mounted into the e2e-runner container")
    stringParam('CLUSTER_REGEX', '', 'K8s cluster regex (name) to supply when requesting cluster')
    stringParam('CLUSTER_VERSION', '', 'K8s cluster version to supply when requesting cluster')
  }

  wrappers {
    buildName('${WORKFLOW_TAG} ${CHART_REPO_TYPE} #${BUILD_NUMBER}')
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
      "${e2eRunnerJob}"
  }
}

job("${chart}-chart-release") {
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
    stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
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
    shell new File("${workspace}/bash/scripts/helm_chart_actions.sh").text +
      """
        #!/usr/bin/env bash

        set -eo pipefail

        download-and-init-helm

        # download chart and index file from aws s3 bucket
        aws s3 cp s3://helm-charts/${chartRepo.production}/${chart}-\${RELEASE_TAG}.tgz .
        aws s3 cp s3://helm-charts/${chartRepo.production}/index.yaml .

        # update index file
        helm repo index . --url https://charts.deis.com/${chartRepo.production} --merge ./index.yaml

        # push updated index file to aws s3 bucket
        aws s3 cp index.yaml s3://helm-charts/${chartRepo.production}/index.yaml
      """.stripIndent().trim()

    conditionalSteps {
      condition {
        status('SUCCESS', 'SUCCESS')
      }
      steps {
        downstreamParameterized {
          trigger("helm-chart-sign") {
            parameters {
              predefinedProps([
                'CHART': chart,
                'VERSION': '${RELEASE_TAG}',
                'CHART_REPO': chartRepo.production,
                'UPSTREAM_SLACK_CHANNEL': repo.slackChannel,
              ])
            }
          }
        }
      }
    }
  }
}
