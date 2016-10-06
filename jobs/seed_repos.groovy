def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common/var.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common/var.groovy"))

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
      string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
    }
  }

  steps {
    shell """
    #!/usr/bin/env bash

    set -eo pipefail

    docker run -v \$PWD:/app -e GITHUB_ACCESS_TOKEN ruby:2.3.1 bash -c 'cd /app && bundle install && for i in \$(./list-repos); do ./seed-repo \$i; done'
    """.stripIndent().trim()
  }
}
