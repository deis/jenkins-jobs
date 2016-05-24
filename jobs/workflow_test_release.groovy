evaluate(new File("${WORKSPACE}/common.groovy"))

import utilities.StatusUpdater

name = 'workflow-test-release'
repoName = 'charts'

job(name) {
  description """
    <p>Runs a given workflow-[RELEASE]-e2e tests chart against a workflow-[RELEASE] chart candidate.</p>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/${repoName}")
      }
      branch('master')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    slackNotifications {
      customMessage(defaults.testJob["reportMsg"])
      notifyAborted()
      notifyFailure()
      notifySuccess()
      includeTestSummary()
     }

     archiveJunit('logs/**/junit*.xml') {
       retainLongStdout(false)
       allowEmptyResults()
     }

     archiveArtifacts {
       pattern('logs/${BUILD_NUMBER}/**')
       onlyIfSuccessful(false)
       fingerprint(false)
       allowEmpty()
     }
   }

  parameters {
   stringParam('HELM_REMOTE_REPO', defaults.helm["remoteRepo"], "Helm remote repo name")
   stringParam('HELM_REMOTE_BRANCH', defaults.helm["remoteBranch"], "Helm remote repo branch")
   stringParam('HELM_REMOTE_NAME', defaults.helm["remoteName"], "Helm remote name")
   stringParam('RELEASE', defaults.workflowRelease, "Release string for resolving workflow-[release](-e2e) charts")
  }

  triggers {
    cron('@daily')
  }

  wrappers {
    buildName('workflow-${RELEASE} #${BUILD_NUMBER}')
    timeout {
      absolute(defaults.testJob["timeoutMins"])
      failBuild()
    }
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("GCLOUD_CREDENTIALS", "246d6550-569b-4925-8cda-e11a4f0d6803")
    }
  }

  steps {
    shell """
      #!/usr/bin/env bash

      set -eo pipefail

      WORKFLOW_CHART="workflow-\${RELEASE}" WORKFLOW_E2E_CHART="workflow-\${RELEASE}-e2e" ./ci.sh
    """.stripIndent().trim()
  }
}
