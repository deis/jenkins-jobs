def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

name = 'k8s-claimer-deploy'
slackChannel = '#ops'

job(name) {
  description """
  <p>Compiles and deploys <a href="https://github.com/deis/k8s-claimer">k8s-claimer</a>
    to the Deis Workflow staging cluster.
  </p>
  <p>
    K8s-Claimer serves as a Kubernetes cluster leaser for running Workflow E2E tests in CI.
  </p>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github('deis/k8s-claimer')
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('master')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    def statusesToNotify = ['SUCCESS', 'FAILURE']
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
                    slack-notify '${slackChannel}' "${buildStatus}"
                  """.stripIndent().trim()
              }
            }
          }
        }
      }
    }
  }

  parameters {
    stringParam('QUAY_USERNAME', 'deis+jenkins', 'Quay account name')
    stringParam('QUAY_EMAIL', 'deis+jenkins@deis.com', 'Quay email address')
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
      string("QUAY_PASSWORD", "8317a529-10f7-40b5-abd4-a42f242f22f0")
      string("DEIS_URL", "1a72f795-58c5-4832-a463-28aa2765ba14")
      string("DEIS_USERNAME", "ed8d00a4-5360-4182-8a86-c9175220296a")
      string("DEIS_PASSWORD", "82b41d2d-53c6-4e24-bf77-108322ff799a")
    }
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      export DEIS_REGISTRY=quay.io/
      docker login -e="\$QUAY_EMAIL" -u="\$QUAY_USERNAME" -p="\$QUAY_PASSWORD" quay.io
      make bootstrap build docker-build docker-push

      curl -sSL http://deis.io/deis-cli/install-v2.sh | bash
      export PATH="\$(pwd):\$PATH"

      make deploy
    '''.stripIndent().trim()
  }
}
