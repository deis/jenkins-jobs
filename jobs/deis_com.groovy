def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

repo_name = 'deis.com'
downstreamJobName = 'deis-com-deploy'

job("deis-com-master") {
  description """
    <ol>
      <li>Watches the <a href="https://github.com/deis/${repo_name}">${repo_name}</a> repo for a commit to master</li>
      <li>Kicks off downstream ${downstreamJobName} job to deploy docs</li>
    </ol>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/deis.com")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('${DEIS_COM_BRANCH}')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  triggers {
    githubPush()
  }

  publishers {
    downstream('deis-com-deploy', 'UNSTABLE')
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    parameters {
      stringParam('DEIS_COM_BRANCH', 'master', 'deis.com branch to build')
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

job("deis-com-pr") {
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
    stringParam('sha1', 'master', 'Specific Git SHA to test')
  }

  triggers {
    githubPush()
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
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
