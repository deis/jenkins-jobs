evaluate(new File("${WORKSPACE}/common.groovy"))

name = 'jenkins-jobs-pr'

job(name) {
  description """
    <ol>
      <li>Watches the <a href="https://github.com/deis/jenkins-jobs">jenkins-jobs</a> repo for a PR commit</li>
      <li>and runs tests against changes</li>
    </ol>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/jenkins-jobs")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
        refspec('+refs/pull/*:refs/remotes/origin/pr/*')
      }
      branch('${sha1}')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  triggers {
    githubPush()

    pullRequest {
      admin('deis-admin')
      cron('H/5 * * * *')
      useGitHubHooks()
      triggerPhrase('OK to test')
      orgWhitelist(['deis'])
      allowMembersOfWhitelistedOrgsAsAdmin()
      extensions {
        commitStatus {
          context('ci/jenkins/pr')
          triggeredStatus("Triggering ${name} tests...")
          startedStatus("Starting ${name} tests...")
          completedStatus('SUCCESS', "Success! All tests passed.")
          completedStatus('FAILURE', 'Build/deploy returned failure(s).')
          completedStatus('ERROR', 'Something went wrong.')
        }
      }
    }
  }

  parameters {
    stringParam('sha1', 'master', 'Specific Git SHA to test')
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      # we currently only have test coverage for bash logic
      cd bash
      make docker-test
    '''.stripIndent().trim()
  }
}
