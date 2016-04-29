evaluate(new File("${WORKSPACE}/common.groovy"))

import utilities.StatusUpdater

name = 'deis-seed-repos'
repoName = 'seed-repo'

job(name) {
  description """
    <p>Runs deis/seed-repo against all of the workflow components.</p>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/${repoName}")
      }
      branch('master')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  triggers {
    githubPush()
  }

  wrappers {
    timeout {
      absolute(defaults.testJob["timeoutMins"])
      failBuild()
    }
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("GITHUB_ACCESS_TOKEN", "8e11254f-44f3-4ddd-bf98-2cabcb7434cd")
    }
  }

  steps {
    repos.each { Map repo ->
      shell """
      #!/usr/bin/env bash

      set -eo pipefail

      ./seed-repo deis/${repo.name}
      """.stripIndent().trim()
    }
  }
}
