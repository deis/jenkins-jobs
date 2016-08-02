def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

name = 'workflow-docs'
downstreamJobName = 'deploy_website'

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
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
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
    parameters {
      stringParam('WORKFLOW_BRANCH', 'master', 'workflow branch to build')
    }
  }

  publishers {
    downstream('deploy_website', 'UNSTABLE')
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      make docker-build docker-test
    '''.stripIndent().trim()
  }
}
