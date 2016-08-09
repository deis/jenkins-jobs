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
      branch('refs/tags/${RELEASE}')
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
    gcloud = "docker run --rm -v  /tmp/workflow-cli-release:/.config -v \${WORKSPACE}/_dist:/upload google/cloud-sdk:latest"
    gcs_bucket = "gs://workflow-cli"
    shell """
      #!/usr/bin/env bash

      set -eo pipefail

      mkdir -p /tmp/workflow-cli-release

      cat "\${GCSKEY}" > /tmp/workflow-cli-release/key.json

      make bootstrap build-revision fileperms

      ${gcloud} gcloud auth activate-service-account -q --key-file /.config/key.json
      ${gcloud} gsutil -mq cp -a public-read -r /upload/* ${gcs_bucket}
      ${gcloud} sh -c 'rm -rf /.config/*'
    """.stripIndent().trim()


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
