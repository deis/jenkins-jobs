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
      notifyRepeatedFailure()
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
      file("GCSKEY", "6561701c-b7b4-4796-83c4-9d87946799e4")
    }
  }

  steps {
    def bucket = "gs://workflow-cli-release"

    def headers  = "-h 'x-goog-meta-ci-job:\${JOB_NAME}' "
        headers += "-h 'x-goog-meta-ci-number:\${BUILD_NUMBER}' "
        headers += "-h 'x-goog-meta-ci-url:\${BUILD_URL}'"

    def script  = "sh -c 'make build-tag "
        script += "&& echo \${GCS_KEY_JSON} | base64 -d - > /tmp/key.json "
        script += "&& gcloud auth activate-service-account -q --key-file /tmp/key.json "
        script += "&& gsutil -mq ${headers} cp -a public-read -r _dist/* ${bucket}'"

    shell """
      #!/usr/bin/env bash

      set -eo pipefail

      git_commit="\$(git rev-parse HEAD)"
      revision_image=quay.io/deisci/workflow-cli-dev:"\${git_commit:0:7}"

      # Build and upload artifacts
      docker run -e GCS_KEY_JSON=\"\${GCSKEY}\" --rm "\${revision_image}" ${script}
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
