def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

repo_name = 'deis.io'
downstreamJobName = 'deis-io-deploy'

job("deis-io-merge") {
  description """
    <ol>
      <li>Watches the <a href="https://github.com/deis/${repo_name}">${repo_name}</a> repo for a commit to gh-pages</li>
      <li>Kicks off downstream ${downstreamJobName} job to deploy</li>
    </ol>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/${repo_name}")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('gh-pages')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  triggers {
    githubPush()
  }

  publishers {
    slackNotifications {
      projectChannel('#marketing')
      notifyAborted()
      notifyFailure()
      notifySuccess()
    }
    downstream("${downstreamJobName}", 'UNSTABLE')
  }

  parameters {
    stringParam('CONTAINER_ENV', '${DEIS_IO_STAGING_ENV}', 'Environment file with AWS API Keys, S3 Buckets and CloudFront values')
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      file('DEIS_IO_STAGING_ENV', '2cfbe7b8-0e93-4e00-8c5b-1731d794d339')
    }
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      make test
    '''.stripIndent().trim()
  }
}

job("deis-io-pr") {
  description """
    <ol>
      <li>Watches the ${repo_name} repo_name for pull requests</li>
    </ol>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/${repo_name}")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
        refspec('+refs/pull/*:refs/remotes/origin/pr/*')
      }
      branch('${sha1}')
    }
  }

  concurrentBuild()
  throttleConcurrentBuilds {
    maxPerNode(defaults.maxBuildsPerNode)
    maxTotal(defaults.maxTotalConcurrentBuilds)
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  triggers {
    pullRequest {
      admin('deis-admin')
      cron('H/5 * * * *')
      useGitHubHooks()
      triggerPhrase('OK to test')
      orgWhitelist(['deis'])
      allowMembersOfWhitelistedOrgsAsAdmin()
      // this plugin will update PR status no matter what,
      // so until we fix this, here are our default messages:
      extensions {
        commitStatus {
          context('ci/jenkins/pr')
          triggeredStatus("Triggering ${repo_name} tests...")
          startedStatus("Starting ${repo_name} tests...")
          completedStatus("SUCCESS", "Merge with caution! Test job(s) may still be in progress...")
          completedStatus("FAILURE", "Tests returned failure(s).")
          completedStatus("ERROR", "Something went wrong.")
        }
      }
    }
  }

  parameters {
    stringParam('DEIS_IO_BRANCH', 'gh-pages', 'deis.io branch to build')
    stringParam('CONTAINER_ENV', '${DEIS_IO_STAGING_ENV}', 'Environment file with AWS API Keys, S3 Buckets and CloudFront values')
    stringParam('sha1', '${DEIS_IO_BRANCH}', 'Specific Git SHA to test')
  }

  triggers {
    githubPush()
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      file('DEIS_IO_STAGING_ENV', '2cfbe7b8-0e93-4e00-8c5b-1731d794d339')
    }
  }

  publishers {
    slackNotifications {
      projectChannel('#marketing')
      notifyAborted()
      notifyFailure()
      notifySuccess()
    }
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      make test

    '''.stripIndent().trim()
  }
}
