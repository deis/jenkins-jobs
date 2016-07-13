evaluate(new File("${WORKSPACE}/common.groovy"))

repoName="workflow-cli"

job("${repoName}-tag-release") {
  description """
    <li>Watches the ${repoName} repo for a new tag to be pushed. (Assumed to be '\${RELEASE}')</li>
    <li>It then checks out the \${RELEASE} tag and builds and deploys binaries, optionally kicking off e2e (default: false)</li>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/${repoName}")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
        refspec('+refs/tags/${RELEASE}:refs/remotes/origin/tags/${RELEASE}')
      }
      branch('tags/${RELEASE}')
    }
  }

  publishers {
    slackNotifications {
      notifyFailure()
      notifySuccess()
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  parameters {
    stringParam('RELEASE', defaults.workflow.release, 'Release to use for tag checkout')
    booleanParam('RUN_E2E', false, 'check to run downstream release e2e job')
  }

  triggers {
    githubPush()
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      file("GCSKEY", "e80fd033-dd76-4d96-be79-6c272726fb82")
    }
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      mkdir -p ${WORKSPACE}/tmp

      cat "${GCSKEY}" > ${WORKSPACE}/tmp/key.json

      GIT_TAG="${RELEASE}" make bootstrap build-revision upload-gcs
    '''.stripIndent().trim()


    conditionalSteps {
      condition {
        booleanCondition('RUN_E2E')
      }
      steps {
        downstreamParameterized {
          trigger(defaults.testJob['release']) {
            parameters {
              predefinedProps([
                "WORKFLOW_BRANCH": 'release-${RELEASE}',
                "WORKFLOW_E2E_BRANCH": 'release-${RELEASE}',
                "RELEASE": '${RELEASE}'
              ])
            }
          }
        }
      }
    }
  }
}
