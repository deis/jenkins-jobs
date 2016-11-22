def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

[
  [type: 'master'],
  [type: 'pr'],
].each { Map config ->
  isMaster = config.type == 'master'
  isPR = config.type == 'pr'

  name = defaults.testJob[config.type]
  repoName = 'charts'

  job(name) {
    description """
      <p>Runs the <a href="https://github.com/deis/workflow-e2e">e2e tests</a> against a
      <a href="https://github.com/deis/charts/tree/master/${defaults.workflow.chartName}">${defaults.workflow.chartName}</a> chart
      using e2e-runner</p>
    """.stripIndent().trim()

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
                  def issueWarning = (isMaster && buildStatus == 'FAILURE')
                  shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                    """
                      message="\$(format-test-job-message ${issueWarning})"
                      slack-notify \${UPSTREAM_SLACK_CHANNEL} ${buildStatus} "\${message}"
                    """.stripIndent().trim()

                  // Update GitHub PR
                  if (isPR) {
                    shell new File("${workspace}/bash/scripts/update_commit_status.sh").text +
                      """
                        if [ -n "\${ACTUAL_COMMIT}" ]; then
                          update-commit-status \
                            ${commitStatus} \
                            \${COMPONENT_REPO} \
                            \${ACTUAL_COMMIT} \
                            \${BUILD_URL} \
                            "${name} job ${buildStatus}"
                        fi
                      """.stripIndent().trim()
                  }
                }
              }
            }
          }
        }
      }
    }

     if (isPR) {
       concurrentBuild()
       throttleConcurrentBuilds {
         maxTotal(defaults.maxWorkflowTestPRConcurrentBuilds)
       }
     } else {
       concurrentBuild()
       throttleConcurrentBuilds {
         maxTotal(defaults.maxWorkflowTestConcurrentBuilds)
       }
     }

    parameters {
      repos.each { Map repo ->
        stringParam(repo.commitEnvVar, '', "${repo.name} commit SHA")
      }
      stringParam('UPSTREAM_BUILD_URL', '', "Upstream build url")
      stringParam('UPSTREAM_SLACK_CHANNEL', defaults.slack.channel, "Upstream Slack channel")
      stringParam('COMPONENT_REPO', '', "Component repo name")
      stringParam('ACTUAL_COMMIT', '', "Component commit SHA")
      stringParam('GINKGO_NODES', '15', "Number of parallel executors to use when running e2e tests")
      stringParam('RELEASE', 'dev', "Release string for resolving workflow-[release](-e2e) charts") // helmc-remove
      stringParam('E2E_RUNNER_IMAGE', 'quay.io/deisci/e2e-runner:canary', "The e2e-runner image")
      stringParam('E2E_DIR', '/home/jenkins/workspace/$JOB_NAME/$BUILD_NUMBER', "Directory for storing workspace files")
      stringParam('E2E_DIR_LOGS', '${E2E_DIR}/logs', "Directory for storing logs. This directory is mounted into the e2e-runner container")
      stringParam('COMMIT_AUTHOR_EMAIL', '', "Commit author email address")
      stringParam('CLUSTER_REGEX', '', 'K8s cluster regex (name) to supply when requesting cluster')
      stringParam('CLUSTER_VERSION', '', 'K8s cluster version to supply when requesting cluster')
      booleanParam('USE_HELM_CLASSIC', false, 'Flag to use Helm Classic (Default: false)') // helmc-remove
    }

    triggers {
      cron('@hourly')
      if (isMaster) {
        githubPush()
      }
    }

    wrappers {
      buildName('${COMPONENT_REPO} #${BUILD_NUMBER}')
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
      if (isPR) { // update commit with pending status while tests run
        shell new File("${workspace}/bash/scripts/update_commit_status.sh").text +
          """
            if [ -n "\${ACTUAL_COMMIT}" ]; then
              update-commit-status \
                "pending" \
                \${COMPONENT_REPO} \
                \${ACTUAL_COMMIT} \
                \${BUILD_URL} \
                "Running e2e tests..."
            fi
          """.stripIndent().trim()

        // helmc-remove
        shell new File("${workspace}/bash/scripts/setup_helmc_environment.sh").text +
          """
            mkdir -p ${defaults.tmpPath}
            setup-helmc-env >> ${defaults.envFile}
          """.stripIndent().trim()
      }

      shell e2eRunnerJob

      if (isMaster) { // send component name and sha to downstream component-promote job
        shell new File("${workspace}/bash/scripts/get_component_and_sha.sh").text +
          """
            #!/usr/bin/env bash

            set -eo pipefail

            mkdir -p ${defaults.tmpPath}
            { get-component-and-sha; \
              echo "UPSTREAM_SLACK_CHANNEL=\${UPSTREAM_SLACK_CHANNEL}"; } >> ${defaults.envFile}
          """.stripIndent().trim()

        conditionalSteps {
          condition {
            not {
              shell "cat \"${defaults.envFile}\" | grep -q SKIP_COMPONENT_PROMOTE"
            }
          }
          steps {
            downstreamParameterized {
              trigger('component-promote') {
                parameters {
                  propertiesFile(defaults.envFile)
                }
              }
            }
          }
        }
      }
    }
  }
}
