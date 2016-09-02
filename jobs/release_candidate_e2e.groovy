evaluate(new File("${WORKSPACE}/common.groovy"))

name = 'release-candidate-e2e'

job(name) {
  description """
    <p>Runs the workflow-[RELEASE]-e2e tests chart against a workflow-[RELEASE] chart including a component release candidate under test</p>
  """.stripIndent().trim()

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    slackNotifications {
      customMessage(defaults.testJob["reportMsg"])
      notifyFailure()
      notifyRepeatedFailure()
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
   }

  concurrentBuild()

  parameters {
    repos.each { Map repo ->
      stringParam(repo.commitEnvVar, '', "${repo.name} commit SHA")
    }
    stringParam('CLI_VERSION', '', "Specific Workflow CLI version")
    stringParam('WORKFLOW_BRANCH', "release-${defaults.workflow.release}", "The branch to use for installing the workflow chart.")
    stringParam('WORKFLOW_E2E_BRANCH', "release-${defaults.workflow.release}", "The branch to use for installing the workflow-e2e chart.")
    stringParam('RELEASE', defaults.workflow.release, "Release string for resolving workflow-[release](-e2e) charts")
    stringParam('HELM_REMOTE_REPO', defaults.helm["remoteRepo"], "The remote repo to use for fetching charts.")
    stringParam('E2E_RUNNER_IMAGE', 'quay.io/deisci/e2e-runner:canary', "The e2e-runner image")
    stringParam('E2E_DIR', '/home/jenkins/workspace/$JOB_NAME/$BUILD_NUMBER', "Directory for storing workspace files")
    stringParam('E2E_DIR_LOGS', '${E2E_DIR}/logs', "Directory for storing logs. This directory is mounted into the e2e-runner container")
    stringParam('CLUSTER_REGEX', '', 'K8s cluster regex (name) to supply when requesting cluster')
    stringParam('CLUSTER_VERSION', '', 'K8s cluster version to supply when requesting cluster')
    stringParam('COMPONENT_NAME', '', 'Component name')
    stringParam('COMPONENT_SHA', '', 'Commit sha used for image tag')
    stringParam('RELEASE_TAG', '', 'Release tag to apply to candidate image')
  }

  wrappers {
    buildName('${COMPONENT_NAME} #${BUILD_NUMBER}')
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
    shell e2eRunnerJob
  }
}
