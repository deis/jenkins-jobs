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
                      "slack-notify '${repo.slackChannel}' '${buildStatus}'"

                    // Update GitHub PR
                    shell new File("${workspace}/bash/scripts/update_commit_status.sh").text +
                      """
                        if [ "\${CHART_REPO_TYPE}" == 'pr' ] && [ -n "\${${repo.commitEnvVar}}" ]; then
                          update-commit-status \
                            ${commitStatus} \
                            ${repo.name} \
                            \${${repo.commitEnvVar}} \
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
        nodeParam('NODE') {
          description('Select node (must be signatory node if SIGN_CHART is \'true\'')
          defaultNodes(['docker', 'linux'])
          allowedNodes(['docker', 'linux', defaults.signingNode])
        }
        choiceParam('CHART_REPO_TYPE', ['dev', 'pr', 'production'], 'Type of chart repo for publishing (default: dev)')
        stringParam('RELEASE_TAG', '', 'Release tag (Default: empty, will use latest git tag for repo)')
        stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
        booleanParam('SIGN_CHART', false, "Sign chart? (default: false/no)")
        stringParam('TRIGGER_WORKFLOW_CHART_PUBLISH', 'true', "Trigger downstream workflow-chart-publish job (default: true)")
        repos.each { Map r ->
          stringParam(r.commitEnvVar, '', r.commitEnvVar == repo.commitEnvVar ?
            "${repo.name} commit SHA for git checkout and chart versioning" : "${r.name} commit SHA for setting <component>.docker_tag in Workflow chart")
        }
      }

      wrappers {
        preBuildCleanup() // Scrub workspace clean before build

        buildName('${RELEASE_TAG} ${CHART_REPO_TYPE} #${BUILD_NUMBER}')
        timestamps()
        colorizeOutput 'xterm'
        credentialsBinding {
          string("AZURE_STORAGE_ACCOUNT", defaults.azure.storageAccount)
          string("AZURE_STORAGE_KEY", defaults.azure.storageAccountKeyID)
          string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
          string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
          string("SIGNING_KEY_PASSPHRASE", '3963b12b-bad3-429b-b1e5-e047a159bf02')
        }
      }

      steps {
        shell new File("${workspace}/bash/scripts/helm_chart_actions.sh").text +
          """
            export ENV_FILE_PATH="${defaults.envFile}"
            mkdir -p ${defaults.tmpPath}

            # checkout PR commit if repo_type 'pr' and value of commit env var non-null
            if [ "\${CHART_REPO_TYPE}" == 'pr' ] && [ -n "\${${repo.commitEnvVar}}" ]; then
              echo "Fetching PR changes from repo '${repo.name}' at commit \${${repo.commitEnvVar}}" 1>&2
              git fetch -q --tags --progress https://github.com/deis/${repo.name}.git +refs/pull/*:refs/remotes/origin/pr/*
              git checkout "\${${repo.commitEnvVar}}"
            fi

            publish-helm-chart ${chart} \${CHART_REPO_TYPE} \${${repo.commitEnvVar}}
          """.stripIndent().trim()

        // Trigger workflow chart publish (will pickup latest component chart published above)
        // IF TRIGGER_WORKFLOW_CHART_PUBLISH is true
        conditionalSteps {
          condition { stringsMatch('${TRIGGER_WORKFLOW_CHART_PUBLISH}', 'true', false) }
          steps {
            downstreamParameterized {
              trigger("workflow-chart-publish") {
                block {
                  buildStepFailure('FAILURE')
                  failure('FAILURE')
                  unstable('UNSTABLE')
                }
                parameters {
                  propertiesFile(defaults.envFile)
                  predefinedProps([
                    'CHART_REPO_TYPE': '${CHART_REPO_TYPE}',
                    'UPSTREAM_SLACK_CHANNEL': repo.slackChannel,
                    'COMPONENT_REPO': repo.name,
                    'GITHUB_STATUS_COMMIT': "\${${repo.commitEnvVar}}",
                  ])
                  // pass all COMPONENT_SHA values on
                  repos.each { Map r ->
                    predefinedProp(r.commitEnvVar, "\${${r.commitEnvVar}}")
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
