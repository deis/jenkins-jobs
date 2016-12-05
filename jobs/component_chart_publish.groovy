def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

repos.each { Map repo ->
  if (repo.chart) {
    def chart = repo.chart
    def jobName = "${chart}-chart-publish"
    job(jobName) {
      description "Publishes a new ${chart} chart release to the chart repo determined by CHART_REPO_TYPE."

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

        def statusesToNotify = [['SUCCESS', 'pending'],['FAILURE', 'failure'],['ABORTED', 'error'],['UNSTABLE', 'failure']]
        postBuildScripts {
          onlyIfBuildSucceeds(false)
          steps {
            statusesToNotify.each { buildStatus, commitStatus ->
              conditionalSteps {
                condition {
                 status(buildStatus, buildStatus)
                  steps {
                    shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                      "slack-notify '${repo.slackChannel}' '${buildStatus}'"

                    // Update GitHub PR
                    shell new File("${workspace}/bash/scripts/update_commit_status.sh").text +
                      """
                        if [ -n "\${ACTUAL_COMMIT}" ] && [ "\${CHART_REPO_TYPE}" == 'pr' ]; then
                          update-commit-status \
                            ${commitStatus} \
                            ${repo.name} \
                            \${ACTUAL_COMMIT} \
                            \${BUILD_URL} \
                            "${jobName} job ${buildStatus}"
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
        choiceParam('CHART_REPO_TYPE', ['dev', 'pr', 'staging', 'production'], 'Type of chart repo for publishing (default: dev)')
        stringParam('RELEASE_TAG', '', 'Release tag (Default: empty, will use latest git tag for repo)')
        stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
        stringParam('ACTUAL_COMMIT', '', "Component commit SHA")
      }

      wrappers {
        buildName('${RELEASE_TAG} ${CHART_REPO_TYPE} #${BUILD_NUMBER}')
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
        shell new File("${workspace}/bash/scripts/helm_chart_actions.sh").text +
              new File("${workspace}/bash/scripts/publish_helm_chart.sh").text +
          """
            export ENV_FILE_PATH="${defaults.envFile}"
            mkdir -p ${defaults.tmpPath}
            set -x
            publish-helm-chart ${chart} \${CHART_REPO_TYPE}
          """.stripIndent().trim()

        conditionalSteps {
          // Trigger downstream signing job IF previous shell step succeeded AND official release
          condition {
            and {
              status('SUCCESS', 'SUCCESS')
            } {
              and {
                shell 'test -n "${RELEASE_TAG}"' } {
                stringsMatch('${CHART_REPO_TYPE}', 'production', false)
              }
            }
          }
          steps {
            downstreamParameterized {
              trigger("helm-chart-sign") {
                parameters {
                  predefinedProps([
                    'CHART': chart,
                    'VERSION': '${RELEASE_TAG}',
                    'CHART_REPO_TYPE': '${CHART_REPO_TYPE}',
                    'UPSTREAM_SLACK_CHANNEL': repo.slackChannel,
                  ])
                }
              }
            }
          }
        }
        // Trigger downstream workflow-chart-publish job if -dev or -pr chart repo
        conditionalSteps {
          condition {
            and { status('SUCCESS', 'SUCCESS')} {
              or {
                stringsMatch('${CHART_REPO_TYPE}', 'dev', false) } {
                stringsMatch('${CHART_REPO_TYPE}', 'pr', false)
              }
            }
          }
          steps {
            downstreamParameterized {
              trigger("workflow-chart-publish") {
                parameters {
                  propertiesFile(defaults.envFile)
                  predefinedProps([
                    'CHART_REPO_TYPE': '${CHART_REPO_TYPE}',
                    'ACTUAL_COMMIT': '${ACTUAL_COMMIT}',
                    'UPSTREAM_SLACK_CHANNEL': repo.slackChannel,
                    'COMPONENT_REPO': repo.name,
                  ])
                }
              }
            }
          }
        }
      }
    }
  }
}
