def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

name = 'deis-io-deploy'

job(name) {
  description """
    <ol>
      <li>Compiles and deploys <a href="https://deis.io">deis.io</a></li>
    </ol>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github('deis/deis.io')
          credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('${DEIS_IO_BRANCH}')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    // Slack notifications?
    slackNotifications {
      projectChannel('#marketing')
      notifyAborted()
      notifyFailure()
      notifySuccess()
    }
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      file('DEIS_IO_STAGING_ENV', '2cfbe7b8-0e93-4e00-8c5b-1731d794d339')
      file('DEIS_IO_PROD_ENV', 'a93c6610-b155-4718-9cb5-4ed7e6ba39e6')
    }
    parameters {
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
