evaluate(new File("${WORKSPACE}/common.groovy"))

[
  [type: 'master'],
  [type: 'pr'],
  // TODO: remove parallel logic when test jobs run parallel by default
  [type: 'parallel'],
].each { Map config ->
  isMaster = config.type == 'master'
  isPR = config.type == 'pr'
  isParallel = config.type == 'parallel'

  testReportMsg = "Test Report: ${JENKINS_URL}job/${defaults.testJob[config.type]}/\${BUILD_NUMBER}/testReport"
  upstreamJobMsg = "Upstream job: ${JENKINS_URL}job/\${UPSTREAM_JOB_NAME}/\${UPSTREAM_BUILD_NUMBER}"
  slackConfig = [
    channel: isParallel ? defaults.slack['channel'] : '#${UPSTREAM_SLACK_CHANNEL}',
    message: isParallel ? testReportMsg : testReportMsg + upstreamJobMsg,
  ]

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
        teamDomain(defaults.slack['teamDomain'])
        integrationToken(defaults.slack['integrationToken'])
        projectChannel(slackConfig.channel)
        customMessage(slackConfig.message)
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
     }

    parameters {
      // create string parameters for every <COMPONENT>_SHA passed from upstream
      repos.each { Map repo ->
       stringParam(repo.commitEnvVar, '', "${repo.name} commit SHA")
     }
    }

    triggers {
      cron(isParallel ? '@hourly': '@daily')
      if (isMaster) {
        githubPush()
      }
    }

    wrappers {
      timestamps()
      colorizeOutput 'xterm'
      credentialsBinding {
        string("GCLOUD_CREDENTIALS", "246d6550-569b-4925-8cda-e11a4f0d6803")
      }
    }

    environmentVariables {
      env('COMMIT', isMaster)
      env('PARALLEL_TESTS', isParallel)
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
