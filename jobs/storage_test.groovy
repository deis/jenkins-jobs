def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common/var.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common/var.groovy"))

name = "storagetype_e2e"

job(name) {
  description """
    <p>Nightly Job runs the <a href="https://github.com/deis/workflow-e2e">e2e tests</a> against a <a href="https://github.com/deis/charts/tree/master/${defaults.workflow.chartName}">${defaults.workflow.chartName}</a> chart configured to GCS by default </p>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/e2e-runner")
      }
      branch('master')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  parameters {
   stringParam('STORAGE_TYPE', 'gcs', "storage backend for helm chart, default is gcs")
   stringParam('HELM_REMOTE_REPO', defaults.helm["remoteRepo"], "The remote repo to use for fetching charts.")
   stringParam('E2E_RUNNER_IMAGE', 'quay.io/deisci/e2e-runner:canary', "The e2e-runner image")
   stringParam('E2E_DIR', '/home/jenkins/workspace/$JOB_NAME/$BUILD_NUMBER', "Directory for storing workspace files")
   stringParam('E2E_DIR_LOGS', '${E2E_DIR}/logs', "Directory for storing logs. This directory is mounted into the e2e-runner container")
   stringParam('RELEASE', "dev", "Release string for resolving workflow-[release](-e2e) charts")
  }

  triggers {
    cron('@daily')
  }

  publishers {
    slackNotifications {
      notifyFailure()
      includeTestSummary()
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
      string("AUTH_TOKEN", "a62d7fe9-5b74-47e3-9aa5-2458ba32da52")
    }
  }

  steps {
    shell 'make docker-test'

    shell """
      #!/usr/bin/env bash

      set -eo pipefail

      mkdir -p ${defaults.tmpPath}
      if [ \${STORAGE_TYPE} == "gcs" ]; then
        export TYPE="GCS"
      else
        export TYPE="AWS"
        export S3_REGION="us-west-1"
      fi
      echo \${TYPE}_REGISTRY_BUCKET="store-registry-\${BUILD_NUMBER}" >> ${defaults.envFile}
      echo \${TYPE}_BUILDER_BUCKET="store-builder-\${BUILD_NUMBER}" >> ${defaults.envFile}
      echo \${TYPE}_DATABASE_BUCKET="store-database-\${BUILD_NUMBER}" >> ${defaults.envFile}
    """.stripIndent().trim()

    shell e2eRunnerJob
  }
}
