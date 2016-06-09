evaluate(new File("${WORKSPACE}/common.groovy"))

name = 'deis.com'
downstreamJobName = 'deploy_website'

job(name) {
  description """
    <ol>
      <li>Watches the <a href="https://github.com/engineyard/${name}">${name}</a> repo for a commit to master</li>
      <li>Kicks off downstream ${downstreamJobName} job to deploy docs</li>
    </ol>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("engineyard/deis.com")
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
    downstream('deploy_website', 'UNSTABLE')
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
