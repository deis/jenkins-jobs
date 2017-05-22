def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

name = 'workflow-docs'
downstreamJobName = 'deis-com-deploy'

job(name) {
  description """
    <ol>
      <li>Watches the <a href="https://github.com/deis/${name}">${name}</a> repo for a commit to master</li>
      <li>Kicks off downstream ${downstreamJobName} job to deploy docs</li>
    </ol>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/workflow")
        credentials(defaults.github.credentialsID)
      }
      branch('${WORKFLOW_BRANCH}')
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
      string("GITHUB_ACCESS_TOKEN", defaults.github.accessTokenCredentialsID)
    }
    parameters {
      stringParam('WORKFLOW_BRANCH', 'master', 'workflow branch to build')
    }
  }

  publishers {
    downstream('deis-com-deploy', 'UNSTABLE')
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      make docker-build docker-test
    '''.stripIndent().trim()
  }
}
