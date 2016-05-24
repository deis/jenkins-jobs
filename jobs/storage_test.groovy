evaluate(new File("${WORKSPACE}/common.groovy"))

name = "storage_backend_e2e"
repoName = 'charts'

job(name) {
  description """
    <p>Nightly Job runs the <a href="https://github.com/deis/workflow-e2e">e2e tests</a> against a <a href="https://github.com/deis/charts/tree/master/${defaults.workflowChart}">${defaults.workflowChart}</a> chart configured to GCS by default </p>
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
  parameters {
   stringParam('STORAGE_TYPE', 'gcs', "storage backend for helm chart, default is gcs")
   stringParam('HELM_REMOTE_REPO', defaults.helm["remoteRepo"], "Helm remote repo name")
   stringParam('HELM_REMOTE_BRANCH', defaults.helm["remoteBranch"], "Helm remote repo branch")
   stringParam('HELM_REMOTE_NAME', defaults.helm["remoteName"], "Helm remote name")
   stringParam('RELEASE', defaults.workflowRelease, "Release string for resolving workflow-[release](-e2e) charts")
  }

  triggers {
    cron('@daily')
  }

  publishers {
    archiveJunit('logs/**/junit*.xml') {
      retainLongStdout(false)
      allowEmptyResults()
    }

    archiveArtifacts {
      pattern('logs/${BUILD_NUMBER}/**')
      onlyIfSuccessful(false)
      fingerprint(false)
      allowEmpty()
    }
  }

  wrappers {
   buildName('Storage_backed-${STORAGE_TYPE} #${BUILD_NUMBER}')
    timeout {
      absolute(30)
      failBuild()
    }
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("GCLOUD_CREDENTIALS", "246d6550-569b-4925-8cda-e11a4f0d6803")
      string("GCS_KEY_JSON", "71dd890b-e4ce-4483-97ca-a7c286c2381c")
      string("AWS_SECRET_KEY","033f76e3-21b8-4bae-a7b3-7f43e07138d3")
      string("AWS_ACCESS_KEY","02a5c84e-3248-4776-acc5-6b6a1fb24dfc")
    }
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      if [ ${STORAGE_TYPE} == "gcs" ]; then
        TYPE="GCS"
      else
        TYPE="AWS"
        export S3_REGION="us-west-1"
      fi
      export ${TYPE}_REGISTRY_BUCKET="deis-registry-${BUILD_NUMBER}"
      export ${TYPE}_BUILDER_BUCKET="deis-builder-${BUILD_NUMBER}"
      export ${TYPE}_DATABASE_BUCKET="deis-database-${BUILD_NUMBER}"

      WORKFLOW_CHART="workflow-\${RELEASE}" WORKFLOW_E2E_CHART="workflow-\${RELEASE}-e2e" ./ci.sh
    '''.stripIndent().trim()

  }
}
