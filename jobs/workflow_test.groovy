evaluate(new File("${WORKSPACE}/common.groovy"))

import utilities.StatusUpdater

[
  [type: 'master'],
  [type: 'pr'],
].each { Map config ->
  isMaster = config.type == 'master'
  isPR = config.type == 'pr'

  name = defaults.testJob[config.type]
  repoName = 'charts'

  commitAuthorMsg = 'Commit Author: ${COMMIT_AUTHOR_EMAIL}'
  testReportMsg = defaults.testJob["reportMsg"]
  upstreamJobMsg = "Upstream job: \${UPSTREAM_BUILD_URL}"

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
      slackNotifications {
        // TODO: re-enable once integrationToken can make use of Jenkins'
        // secure credentials handling:
        // https://github.com/jenkinsci/slack-plugin/pull/208
        // teamDomain(defaults.slack['teamDomain'])
        // integrationToken('${SLACK_INTEGRATION_TOKEN}')
        // projectChannel('#${UPSTREAM_SLACK_CHANNEL}')
        customMessage([commitAuthorMsg, testReportMsg, upstreamJobMsg].join('\n'))
        notifyFailure()
        notifyRepeatedFailure()
        showCommitList()
        includeTestSummary()
       }

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

       if (isPR) {
         def statuses = [['SUCCESS', 'success'],['FAILURE', 'failure'],['ABORTED', 'error'],['UNSTABLE', 'failure']]
         postBuildScripts {
           onlyIfBuildSucceeds(false)
           steps {
             statuses.each { buildStatus, commitStatus ->
               conditionalSteps {
                 condition {
                   status(buildStatus, buildStatus)
                   steps {
                     shell StatusUpdater.updateStatus(
                       commitStatus: commitStatus, repoName: '${COMPONENT_REPO}', commitSHA: '${ACTUAL_COMMIT}', description: "${name} job ${buildStatus}")
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
      stringParam('UPSTREAM_SLACK_CHANNEL', '', "Upstream Slack channel")
      stringParam('COMPONENT_REPO', '', "Component repo name")
      stringParam('ACTUAL_COMMIT', '', "Component commit SHA")
      stringParam('GINKGO_NODES', '15', "Number of parallel executors to use when running e2e tests")
      stringParam('RELEASE', 'dev', "Release string for resolving workflow-[release](-e2e) charts")
      stringParam('E2E_RUNNER_IMAGE', 'quay.io/deisci/e2e-runner:canary', "The e2e-runner image")
      stringParam('E2E_DIR', '/home/jenkins/workspace/$JOB_NAME/$BUILD_NUMBER', "Directory for storing workspace files")
      stringParam('E2E_DIR_LOGS', '${E2E_DIR}/logs', "Directory for storing logs. This directory is mounted into the e2e-runner container")
      stringParam('COMMIT_AUTHOR_EMAIL', 'n/a', "Commit author email address")
      stringParam('CLUSTER_REGEX', '', 'K8s cluster regex (name) to supply when requesting cluster')
      stringParam('CLUSTER_VERSION', '', 'K8s cluster version to supply when requesting cluster')
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
      }
    }

    steps {
      if (isPR) { // update commit with pending status while tests run
        shell StatusUpdater.updateStatus(
          commitStatus: 'pending', repoName: '${COMPONENT_REPO}', commitSHA: '${ACTUAL_COMMIT}', description: 'Running e2e tests...')

        setupHelmcEnv = new File("${WORKSPACE}/bash/scripts/setup_helmc_environment.sh").text
        setupHelmcEnv += """
          mkdir -p ${defaults.tmpPath}
          setup-helmc-env >> ${defaults.envFile}
        """.stripIndent().trim()

        shell setupHelmcEnv
      }

      shell e2eRunnerJob

      if (isMaster) { // send component name and sha to downstream component-promote job
        main  = new File("${WORKSPACE}/bash/scripts/get_component_and_sha.sh").text
        main += """
          #!/usr/bin/env bash

          set -eo pipefail

          mkdir -p ${defaults.tmpPath}
          get-component-and-sha >> ${defaults.envFile}
        """.stripIndent().trim()

        shell main

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
