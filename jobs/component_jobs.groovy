def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

repos.each { Map repo ->
  if(repo.buildJobs != false) {
    [
      // for each repo, create a <repo.name>-master and <repo.name>-pr job
      [type: 'master'],
      [type: 'pr'],
    ].each { Map config ->
      isMaster = config.type == 'master'
      isPR = config.type == 'pr'

      name = isMaster ? repo.name : "${repo.name}-pr"

      job(name) {
        description "Watches the ${repo.name} repo for a ${config.type} commit and triggers downstream pipelines"

        scm {
          git {
            remote {
              github("deis/${repo.name}")
              credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
              if (isPR) {
                refspec('+refs/pull/*:refs/remotes/origin/pr/*')
              }
            }
            branch('${sha1}')
          }
        }

        def statusesToNotify = ['FAILURE']
        publishers {
          postBuildScripts {
            onlyIfBuildSucceeds(false)
            steps {
              statusesToNotify.each { buildStatus ->
                conditionalSteps {
                  condition {
                   status(buildStatus, buildStatus)
                    steps {
                      shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                        """
                          slack-notify "${repo.slackChannel}" "${buildStatus}"
                        """.stripIndent().trim()
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
            maxPerNode(defaults.maxBuildsPerNode)
            maxTotal(defaults.maxTotalConcurrentBuilds)
          }
        }

        logRotator {
          daysToKeep defaults.daysToKeep
        }

        if (isPR) { // set up GitHubPullRequest build trigger
          triggers {
            pullRequest {
              admin('deis-admin')
              cron('H/5 * * * *')
              useGitHubHooks()
              triggerPhrase('OK to test')
              orgWhitelist(['deis'])
              allowMembersOfWhitelistedOrgsAsAdmin()
              // this plugin will update PR status no matter what,
              // so until we fix this, here are our default messages:
              extensions {
                commitStatus {
                  context('ci/jenkins/pr')
                  triggeredStatus("Triggering ${repo.name} build/test pipeline...")
                  startedStatus("Starting ${repo.name} build/test pipeline...")
                  completedStatus('SUCCESS', "${repo.name} build/test pipeline SUCCESS!")
                  completedStatus('FAILURE', "${repo.name} build/test pipeline FAILURE.")
                  completedStatus('ERROR', 'Something went wrong.')
                }
              }
            }
          }
        }

        parameters {
          stringParam('DOCKER_USERNAME', 'deisbot', 'Docker Hub account name')
          stringParam('DOCKER_EMAIL', 'dummy-address@deis.com', 'Docker Hub email address')
          stringParam('QUAY_USERNAME', 'deisci+jenkins', 'Quay account name')
          stringParam('QUAY_EMAIL', 'deisci+jenkins@deis.com', 'Quay email address')
          stringParam('sha1', 'master', 'Specific Git SHA to test')
        }

        triggers {
          githubPush()
        }

        wrappers {
          timestamps()
          colorizeOutput 'xterm'
          credentialsBinding {
            string("DOCKER_PASSWORD", "0d1f268f-407d-4cd9-a3c2-0f9671df0104")
            string("QUAY_PASSWORD", "c67dc0a1-c8c4-4568-a73d-53ad8530ceeb")
            string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
            string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
          }
        }

        steps {
          main = [
            new File("${workspace}/bash/scripts/get_actual_commit.sh").text,
            new File("${workspace}/bash/scripts/find_required_commits.sh").text,
            new File("${workspace}/bash/scripts/skip_e2e_check.sh").text,
          ].join('\n')

          repo.components.each { Map component ->
            cdComponentDir = component.name == repo.name ?: "cd ${component.name}"
            dockerPush = isPR ? 'docker-immutable-push' : 'docker-push'

            def script = main
            script += """
              #!/usr/bin/env bash

              set -eo pipefail

              ${cdComponentDir}

              git_commit="\$(get-actual-commit ${repo.name} \${ghprbActualCommit})"

              make bootstrap || true

              export IMAGE_PREFIX=deisci VERSION="git-\${git_commit:0:7}"
              docker login -e="\$DOCKER_EMAIL" -u="\$DOCKER_USERNAME" -p="\$DOCKER_PASSWORD"
              # build once with "docker --pull --no-cache" to avoid stale layers
              DEIS_REGISTRY='' DOCKER_BUILD_FLAGS="--pull --no-cache" make docker-build ${dockerPush}
              docker login -e="\$QUAY_EMAIL" -u="\$QUAY_USERNAME" -p="\$QUAY_PASSWORD" quay.io
              DEIS_REGISTRY=quay.io/ make docker-build ${dockerPush}

              # populate env file for passing to downstream job(s)
              mkdir -p ${defaults.tmpPath}
              mkdir -p "\$(dirname ${component.envFile})"
              { echo COMMIT_AUTHOR_EMAIL="\$(echo "\${git_commit}" | git --no-pager show -s --format='%ae')"; \
                echo ACTUAL_COMMIT="\${git_commit}"; \
                echo ${repo.commitEnvVar}="\${git_commit}"; \
                echo COMPONENT_NAME="${component.name}"; \
                echo COMPONENT_SHA="\${git_commit}"; \
                echo UPSTREAM_SLACK_CHANNEL="${repo.slackChannel}"; \
                echo "\$(find-required-commits "\${git_commit}")"; \
                echo "\$(check-skip-e2e "\${git_commit}")"; } | tee -a ${component.envFile} ${defaults.envFile}
            """.stripIndent().trim()

            shell script
          }

          def chartRepoType = isMaster ? 'dev' : 'pr'

          // Downstream jobs/pipeline START
          // IF commit has changes in 'charts' sub-directory, trigger the following:
          if (repo.chart) {
            conditionalSteps {
              condition { shell checkForChartChanges }
              steps {
                // Trigger component chart publish
                downstreamParameterized {
                  trigger("${repo.chart}-chart-publish") {
                    block {
                      buildStepFailure('FAILURE')
                      failure('FAILURE')
                      unstable('UNSTABLE')
                    }
                    parameters {
                      propertiesFile(defaults.envFile)
                      predefinedProps(['CHART_REPO_TYPE': chartRepoType])
                    }
                  }
                }
              }
            }
          }

          // Trigger e2e run if SKIP_E2E is NOT set AND there are NOT changes in the 'charts' sub-directory
          if (repo.runE2e) {
            conditionalSteps {
              condition {
                and {
                  not { shell "cat \"${defaults.envFile}\" | grep -q SKIP_E2E" } } {
                  not { shell checkForChartChanges }
                }
              }
              steps {
                downstreamParameterized {
                  trigger(defaults.testJob.name) {
                    block {
                      buildStepFailure('FAILURE')
                      failure('FAILURE')
                      unstable('UNSTABLE')
                    }
                    parameters {
                      propertiesFile(defaults.envFile)
                      predefinedProps([
                        'UPSTREAM_BUILD_URL': '${BUILD_URL}',
                        'COMPONENT_REPO': repo.name,
                        'CHART_REPO_TYPE': 'dev', // ensure downstream test job installs Workflow from 'dev' repo instead of 'pr' (which may have broken chart)
                      ])
                    }
                  }
                }
              }
            }
          }

          // Trigger downstream component-promote job assuming e2e success
          if (isMaster) {
            repo.components.each { Map component ->
              conditionalSteps {
                condition {
                  and { status('SUCCESS', 'SUCCESS')} {
                    not { shell "cat \"${defaults.envFile}\" | grep -q SKIP_COMPONENT_PROMOTE" }
                  }
                }
                steps {
                  downstreamParameterized {
                    trigger('component-promote') {
                      parameters {
                        propertiesFile(component.envFile)
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
  }
}
