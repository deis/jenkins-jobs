def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

repos.each { Map repo ->
  if (repo.chart) {
    def chart = repo.chart

    job("${chart}-chart-publish") {
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
        stringParam('RELEASE_TAG', '', 'Release tag (Default: empty, will use latest git tag for repo)')
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
        shell new File("${workspace}/bash/scripts/helm_chart_actions.sh").text +
              new File("${workspace}/bash/scripts/publish_helm_chart.sh").text +
          """
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
                shell 'test -n "${RELEASE_TAG}"'
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
        // Trigger downstream workflow-chart-publish job if -dev chart repo
        conditionalSteps {
          condition {
            and {
              status('SUCCESS', 'SUCCESS')
            } {
              and {
                stringsMatch('${CHART_REPO_TYPE}', 'dev', false) // 'false' for ignoreCase boolean arg
              }
            }
            steps {
              downstreamParameterized {
                trigger("workflow-chart-publish") {
                  parameters {
                    predefinedProps([
                      'CHART_REPO_TYPE': '${CHART_REPO_TYPE}',
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
}
