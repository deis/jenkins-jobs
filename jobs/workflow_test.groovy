evaluate(new File("${WORKSPACE}/common.groovy"))

[
  [type: 'master'],
  [type: 'pr'],
].each { Map config ->
  isMaster = config.type == 'master'
  isPR = config.type == 'pr'

  testReportMsg = "Test Report: ${JENKINS_URL}job/${defaults.testJob[config.type]}/\${BUILD_NUMBER}/testReport"
  upstreamJobMsg = "Upstream job: ${JENKINS_URL}job/\${UPSTREAM_JOB_NAME}/\${UPSTREAM_BUILD_NUMBER}"

  job(defaults.testJob[config.type]) {
    description """
      <p>Runs the <a href="https://github.com/deis/workflow-e2e">e2e tests</a> against a <a href="https://github.com/deis/charts/tree/master/${defaults.workflowChart}">${defaults.workflowChart}</a> chart</p>
    """.stripIndent().trim()

    scm {
      git {
        remote {
          github("deis/charts")
          credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
        }
        branch('master')
      }
    }

    logRotator {
      numToKeep defaults.numBuildsToKeep
    }

    if (isPR) {
      concurrentBuild()
      throttleConcurrentBuilds {
        maxPerNode(defaults.maxBuildsPerNode)
        maxTotal(defaults.maxTotalConcurrentBuilds)
      }
    }

    publishers {
      slackNotifications {
        // TODO: re-enable once integrationToken can make use of Jenkins'
        // secure credentials handling:
        // https://github.com/jenkinsci/slack-plugin/pull/208
        // teamDomain(defaults.slack['teamDomain'])
        // integrationToken('${SLACK_INTEGRATION_TOKEN}')
        // projectChannel('#${UPSTREAM_SLACK_CHANNEL}')
        customMessage([testReportMsg, upstreamJobMsg].join('\n'))
        notifyAborted()
        notifyFailure()
        notifySuccess()
        showCommitList()
        includeTestSummary()
       }

       if (isMaster) {
         git {
           pushOnlyIfSuccess()
           branch('origin', 'master')
         }
       }

       archiveJunit('logs/**/junit*.xml') {
         retainLongStdout(false)
       }

       archiveArtifacts {
         pattern('logs/${BUILD_NUMBER}/**')
         onlyIfSuccessful(false)
         fingerprint(false)
       }

       def statuses = [['SUCCESS', 'success'],['FAILURE', 'failure'],['ABORTED', 'error']]
       postBuildScripts {
         steps {
           statuses.each { buildStatus, commitStatus ->
             conditionalSteps {
               condition {
                 status(buildStatus, buildStatus)
                 steps {
                   shell curlStatus(buildStatus: buildStatus, commitStatus: commitStatus, jobName: name, repoName: repo.name)
                 }
               }
             }
           }
         }
       }
     }

    parameters {
      // create string parameters for every <COMPONENT>_SHA passed from upstream
      repos.each { Map repo ->
       stringParam(repo.commitEnvVar, '', "${repo.name} commit SHA")
     }
    }

    triggers {
      cron('@daily')
      if (isMaster) {
        githubPush()
      }
    }

    wrappers {
      timeout {
        absolute(25)
        failBuild()
      }
      timestamps()
      colorizeOutput 'xterm'
      credentialsBinding {
        string("GCLOUD_CREDENTIALS", "246d6550-569b-4925-8cda-e11a4f0d6803")
      }
    }

    environmentVariables {
      env('COMMIT', isMaster)
      env('PARALLEL_TESTS', true)
    }

    steps {
      shell """
        #!/usr/bin/env bash

        set -eo pipefail

        ./ci.sh
        if [ "\${COMMIT}" == "true" ]; then
          ${defaults.bumpverCommitCmd}
        fi
      """.stripIndent().trim()
    }
  }
}
