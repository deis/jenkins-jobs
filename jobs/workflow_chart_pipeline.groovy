def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

def repo = repos.find{ it.name == 'workflow' }
def chart = repo.chart
def chartRepo = [dev: "${chart}-dev", staging: "${chart}-staging", production: chart]

job("${chart}-chart-publish") {
  description "Publishes a Workflow chart to the chart repo determined by CHART_REPO_TYPE."

  scm {
    git {
      remote {
        github("deis/${repo.name}")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('master')
    }
  }

  triggers {
    githubPush()
  }

  concurrentBuild()
  throttleConcurrentBuilds {
    maxTotal(defaults.maxTotalConcurrentBuilds)
  }

  publishers {
    wsCleanup() // Scrub workspace clean after build

    def statusesToNotify = [['SUCCESS', 'success'],['FAILURE', 'failure'],['ABORTED', 'error'],['UNSTABLE', 'failure']]
    postBuildScripts {
      onlyIfBuildSucceeds(false)
      steps {
        statusesToNotify.each { buildStatus, commitStatus ->
          conditionalSteps {
            condition {
             status(buildStatus, buildStatus)
              steps {
                shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                  "slack-notify \${UPSTREAM_SLACK_CHANNEL} '${buildStatus}'"

                // Update GitHub PR
                shell new File("${workspace}/bash/scripts/update_commit_status.sh").text +
                  """
                    if [ -n "\${ACTUAL_COMMIT}" ] && [ "\${CHART_REPO_TYPE}" == 'pr' ]; then
                      update-commit-status \
                        ${commitStatus} \
                        \${COMPONENT_REPO:-${repo.name}} \
                        \${ACTUAL_COMMIT} \
                        \${BUILD_URL} \
                        "${chart}-chart-publish job ${buildStatus}"
                    fi
                  """.stripIndent().trim()
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
    choiceParam('CHART_REPO_TYPE', ['dev', 'pr'], 'Type of chart repo for publishing (default: dev)')
    stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
    stringParam('UPSTREAM_SLACK_CHANNEL', repo.slackChannel, "Upstream Slack channel")
    stringParam('COMPONENT_REPO', '', "Component repo name")
    stringParam('COMPONENT_CHART_VERSION', '', "Component chart version")
    stringParam('ACTUAL_COMMIT', '', "Actual commit SHA for versioning chart; will be tied to COMPONENT_REPO if provided")
  }

  wrappers {
    buildName('${COMPONENT_REPO} ${CHART_REPO_TYPE} #${BUILD_NUMBER}')
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("AWS_ACCESS_KEY_ID", '57e64439-4521-4a4f-9315-eac10ecdea75')
      string("AWS_SECRET_ACCESS_KEY", '313da896-1579-41fa-9c70-c6b13d938e9c')
      string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
  }

  steps {
    def components = repos.collectMany {
      it.workflowComponent ? [it.chart+':'+it.name] : [] }.join(' ') as String

    shell new File("${workspace}/bash/scripts/get_latest_component_release.sh").text +
          new File("${workspace}/bash/scripts/helm_chart_actions.sh").text +
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
            parameters {
              propertiesFile(defaults.envFile)
              predefinedProps([
                'CHART_REPO_TYPE': '${CHART_REPO_TYPE}',
                'HELM_VERSION': '${HELM_VERSION}',
                'ACTUAL_COMMIT': '${ACTUAL_COMMIT}',
                'COMPONENT_REPO': '${COMPONENT_REPO}',
                'UPSTREAM_SLACK_CHANNEL': '${UPSTREAM_SLACK_CHANNEL}',
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

  concurrentBuild()
  throttleConcurrentBuilds {
    maxTotal(defaults.maxWorkflowTestConcurrentBuilds)
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

    def statusesToNotify = [['SUCCESS', 'success'],['FAILURE', 'failure'],['ABORTED', 'error'],['UNSTABLE', 'failure']]
    postBuildScripts {
      onlyIfBuildSucceeds(false)
      steps {
        statusesToNotify.each { buildStatus, commitStatus ->
          conditionalSteps {
            condition {
             status(buildStatus, buildStatus)
              steps {
                // Dispatch Slack notification
                def issueWarning = (buildStatus == 'FAILURE')
                shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                  """
                    message="\$(format-test-job-message ${issueWarning})"
                    slack-notify \${UPSTREAM_SLACK_CHANNEL} ${buildStatus} "\${message}"
                  """.stripIndent().trim()

                // Update GitHub PR
                shell new File("${workspace}/bash/scripts/update_commit_status.sh").text +
                  """
                    if [ -n "\${ACTUAL_COMMIT}" ] && [ "\${CHART_REPO_TYPE}" == 'pr' ]; then
                      update-commit-status \
                        ${commitStatus} \
                        \${COMPONENT_REPO:-${repo.name}} \
                        \${ACTUAL_COMMIT} \
                        \${BUILD_URL} \
                        "${chart}-chart-e2e job ${buildStatus}"
                    fi
                  """.stripIndent().trim()
              }
            }
          }
        }
      }
    }
  }

  parameters {
    stringParam('WORKFLOW_TAG', '', 'Workflow chart version (default: empty, will pull latest from given chart repo)')
    stringParam('WORKFLOW_E2E_TAG', '', 'Workflow-E2E chart version (default: empty, will pull latest from given chart repo)')
    choiceParam('CHART_REPO_TYPE', ['dev', 'pr', 'staging', 'production'], 'Type of chart repo for publishing (default: dev)')
    stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
    stringParam('GINKGO_NODES', '15', "Number of parallel executors to use when running e2e tests")
    stringParam('E2E_RUNNER_IMAGE', 'quay.io/deisci/e2e-runner:canary', "The e2e-runner image")
    stringParam('E2E_DIR', '/home/jenkins/workspace/$JOB_NAME/$BUILD_NUMBER', "Directory for storing workspace files")
    stringParam('E2E_DIR_LOGS', '${E2E_DIR}/logs', "Directory for storing logs. This directory is mounted into the e2e-runner container")
    stringParam('CLUSTER_REGEX', '', 'K8s cluster regex (name) to supply when requesting cluster')
    stringParam('CLUSTER_VERSION', '', 'K8s cluster version to supply when requesting cluster')
    stringParam('UPSTREAM_BUILD_URL', '', "Upstream build url")
    stringParam('UPSTREAM_SLACK_CHANNEL', defaults.slack.channel, "Upstream Slack channel")
    stringParam('COMMIT_AUTHOR_EMAIL', '', "Commit author email address")
    stringParam('COMPONENT_REPO', '', "Component repo name")
    stringParam('ACTUAL_COMMIT', '', "Component commit SHA")
    repos.each { Map r ->
      stringParam(r.commitEnvVar, '', "${r.name} commit SHA for setting <component>.docker_tag in Workflow chart")
    }
  }

  wrappers {
    buildName('${WORKFLOW_TAG} ${COMPONENT_REPO} ${CHART_REPO_TYPE} #${BUILD_NUMBER}')
    timeout {
      absolute(defaults.testJob["timeoutMins"])
      failBuild()
    }
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("AUTH_TOKEN", "a62d7fe9-5b74-47e3-9aa5-2458ba32da52")
      string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
  }

  steps {
    // Notify GitHub PR of pending e2e run, if applicable
    shell new File("${workspace}/bash/scripts/update_commit_status.sh").text +
      """
        if [ -n "\${ACTUAL_COMMIT}" ] && [ "\${CHART_REPO_TYPE}" == 'pr' ]; then
          update-commit-status \
            "pending" \
            \${COMPONENT_REPO:-${repo.name}} \
            \${ACTUAL_COMMIT} \
            \${BUILD_URL} \
            "Running e2e tests..."
        fi
      """.stripIndent().trim()

    // Run tests
    shell new File("${workspace}/bash/scripts/get_latest_component_release.sh").text +
      "${e2eRunnerJob}"
  }
}

job("${chart}-chart-stage") {
  description "Publishes a signed Workflow chart to the 'staging' chart repo"

  scm {
    git {
      remote {
        github("deis/${repo.name}")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('master')
    }
  }

  concurrentBuild()
  throttleConcurrentBuilds {
    maxTotal(defaults.maxTotalConcurrentBuilds)
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
    nodeParam('SIGNATORY_NODE'){
      description('select signatory node')
      defaultNodes([defaults.signingNode])
      allowedNodes([defaults.signingNode])
    }
    stringParam('RELEASE_TAG', '', 'Release tag')
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
      string("SIGNING_KEY_PASSPHRASE", '3963b12b-bad3-429b-b1e5-e047a159bf02')
    }
  }

  steps {
    def components = repos.collectMany {
      it.workflowComponent ? [it.chart+':'+it.name] : [] }.join(' ') as String

    shell new File("${workspace}/bash/scripts/get_latest_component_release.sh").text +
          new File("${workspace}/bash/scripts/helm_chart_actions.sh").text +
      """
        export COMPONENT_CHART_AND_REPOS="${components}"

        publish-helm-chart ${chart} 'staging'
      """.stripIndent().trim()

    conditionalSteps {
      condition { status('SUCCESS', 'SUCCESS') }
      steps {
        // Trigger e2e against staged release candidate chart
        downstreamParameterized {
          trigger("${chart}-chart-e2e") {
            parameters {
              propertiesFile(defaults.envFile)
              predefinedProps([
                'CHART_REPO_TYPE': 'staging',
                'HELM_VERSION': '${HELM_VERSION}',
                'UPSTREAM_SLACK_CHANNEL': repo.slackChannel,
              ])
            }
          }
        }
      }
    }
  }
}

job("${chart}-chart-release") {
  description "Publishes official Workflow chart by copying e2e-approved, signed chart from the `${chartRepo.staging}` repo to the `${chartRepo.production}` repo."

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
    stringParam('RELEASE_TAG', '', 'Release tag')
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
        condition { status('SUCCESS', 'SUCCESS') }
        steps {
        // Trigger job to verify signature of release chart
        downstreamParameterized {
          trigger("helm-chart-verify") {
            parameters {
              predefinedProps([
                'CHART': chart,
                'VERSION': '${RELEASE_TAG}',
                'CHART_REPO_TYPE': 'production',
                'UPSTREAM_SLACK_CHANNEL': repo.slackChannel,
              ])
            }
          }
        }
      }
    }
  }
}
