evaluate(new File("${WORKSPACE}/common.groovy"))

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
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("GITHUB_ACCESS_TOKEN", "8e11254f-44f3-4ddd-bf98-2cabcb7434cd")
    }
  }

  steps {
    shell """
    #!/usr/bin/env bash

    set -eo pipefail

    bundle install

    for i in \$(./list-repos); do ./seed-repo \$i; done
    """.stripIndent().trim()
  }
}
