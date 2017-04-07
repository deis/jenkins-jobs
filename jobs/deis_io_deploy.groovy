def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

name = 'deis-io-deploy'
slackChannel = '#marketing'

job(name) {
  description """
    <ol>
      <li>Compiles and deploys <a href="https://deis.io">deis.io</a></li>
    </ol>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github('${DEIS_IO_ORG}/deis.io')
          credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('${DEIS_IO_BRANCH}')
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

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      file('DEIS_IO_STAGING_ENV', '2cfbe7b8-0e93-4e00-8c5b-1731d794d339')
      file('DEIS_IO_PROD_ENV', 'a93c6610-b155-4718-9cb5-4ed7e6ba39e6')
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
    parameters {
      stringParam('DEIS_IO_ORG', 'deis', 'GitHub organization to use.')
      stringParam('DEIS_IO_BRANCH', 'gh-pages', 'deis.io branch to deploy')
      stringParam('CONTAINER_ENV', '${DEIS_IO_PROD_ENV}', 'Environment file with AWS API Keys, S3 Buckets and CloudFront values')
    }
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      cd "${WORKSPACE}"
      make prep build deploy
    '''.stripIndent().trim()
  }
}
