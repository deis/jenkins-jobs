def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

repos.each { Map repo ->
  if(repo.buildJobs != false) {
    name = "${repo.name}-release"

    job(name) {
      description """
        <ol>
          <li>Watches the ${repo.name} repo for a git tag push. (It can also be triggered manually, supplying a value for TAG.)</li>
          <li>Unless TAG is set (manual trigger), this job only runs off the latest tag.</li>
          <li>The commit at HEAD of tag is then used to locate the release candidate image(s).</li>
          <li>Kicks off downstream e2e job to vet candidate image(s).</li>
          <li>Provided e2e tests pass, retags release candidate(s) with official semver tag in the 'deis' registry orgs.</li>
        </ol>
      """.stripIndent().trim()

      scm {
        git {
          remote {
            github("deis/${repo.name}")
            credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
            refspec('+refs/tags/*:refs/remotes/origin/tags/*')
          }
          branch('*/tags/*')
        }
      }

      logRotator {
        daysToKeep defaults.daysToKeep
      }

      publishers {
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
        stringParam('TAG', '', 'Specific tag to release')
        stringParam('CLI_VERSION', defaults.cli.release, 'Specific Workflow CLI version to use in downstream e2e run, if applicable')
      }

      triggers {
        githubPush()
      }

      wrappers {
        buildName('${GIT_BRANCH} ${TAG} #${BUILD_NUMBER}')
        timestamps()
        colorizeOutput 'xterm'
        credentialsBinding {
          string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
          string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
        }
      }

      steps {
        main = [
          new File("${workspace}/bash/scripts/get_latest_tag.sh").text,
          new File("${workspace}/bash/scripts/locate_release_candidate.sh").text,
        ].join('\n')

        repo.components.each{ Map component ->
          main += """
            #!/usr/bin/env bash

            set -eo pipefail

            tag="\$(get-latest-tag ${component.name})"
            commit="\$(git checkout "\${tag}" && git rev-parse HEAD)"
            echo "Checked out tag '\${tag}' and will pass commit at HEAD (\${commit}) to downstream job."

            echo "Locating release candidate based on tag commit '\${commit}'..."
            result="\$(locate-release-candidate ${component.name} "\${commit}" "\${tag}")"

            mkdir -p ${defaults.tmpPath}
            mkdir -p "\$(dirname ${component.envFile})"
            { echo "\${result}"; \
              echo "CLI_VERSION=\${CLI_VERSION}"; \
              echo "UPSTREAM_SLACK_CHANNEL=${repo.slackChannel}"; } | tee -a ${component.envFile} ${defaults.envFile}
          """.stripIndent()
        }

        shell main

        // Downstream jobs/pipeline START
        // Trigger job to promote candidate image to 'prod' (deis) image registries
        conditionalSteps {
          condition { status('SUCCESS', 'SUCCESS') }
          steps {
            repo.components.each{ Map component ->
              downstreamParameterized {
                trigger('release-candidate-promote') {
                  block {
                    buildStepFailure('FAILURE')
                    failure('FAILURE')
                    unstable('UNSTABLE')
                  }
                  parameters {
                    propertiesFile(component.envFile)
                  }
                }
              }
            }
          }
        }

        // Trigger jobs to publish chart to both 'dev' and 'production' chart repos
        conditionalSteps {
          condition { status('SUCCESS', 'SUCCESS') }
          steps {
            if (repo.chart) {
              downstreamParameterized {
                trigger("${repo.chart}-chart-publish") {
                  block {
                    buildStepFailure('FAILURE')
                    failure('FAILURE')
                    unstable('UNSTABLE')
                  }
                  parameters {
                    propertiesFile(defaults.envFile)
                    predefinedProps([
                      'CHART_REPO_TYPE': 'dev',
                      'TRIGGER_WORKFLOW_CHART_PUBLISH': 'false',
                    ])
                  }
                }
              }
              downstreamParameterized {
                trigger("${repo.chart}-chart-publish") {
                  block {
                    buildStepFailure('FAILURE')
                    failure('FAILURE')
                    unstable('UNSTABLE')
                  }
                  parameters {
                    propertiesFile(defaults.envFile)
                    predefinedProps([
                      'CHART_REPO_TYPE': 'production',
                      'NODE': defaults.signingNode[0],
                      'SIGN_CHART': true,
                      'TRIGGER_WORKFLOW_CHART_PUBLISH': 'false',
                    ])
                  }
                }
              }
            }
          }
        }

        // Trigger downstream chart signature verification job
        conditionalSteps {
          condition { status('SUCCESS', 'SUCCESS') }
          steps {
            downstreamParameterized {
              trigger("helm-chart-verify") {
                parameters {
                  propertiesFile(defaults.envFile)
                  predefinedProps([
                    'CHART': repo.chart,
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
  }
}
