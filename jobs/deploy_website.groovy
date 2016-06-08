evaluate(new File("${WORKSPACE}/common.groovy"))

name = 'deploy_website'

job(name) {
  description """
    <ol>
      <li>Compiles and deploys websites to <a href="https://deis.com">deis.com</a></li>
    </ol>
  """.stripIndent().trim()

  multiscm {
   git {
     remote {
         github('deis/gutenberg')
         credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
     }
     extensions {
         relativeTargetDirectory('gutenberg')
     }
     branch('master')
   }
   git {
     remote {
         github('deis/workflow')
         credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
     }
     extensions {
         relativeTargetDirectory('workflow')
     }
     branch('master')
   }
   git {
     remote {
         github('engineyard/deis.com')
         credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
     }
     extensions {
         relativeTargetDirectory('deis.com')
     }
     branch('master')
   }
 }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    // Slack notifications?
    slackNotifications {
      projectChannel('#marketing')
      notifyAborted()
      notifyFailure()
      notifySuccess()
    }
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      file('GUTENBERG_STAGING_ENV', '7d29c809-9480-47ae-91aa-a25f43e58897')
    }
    parameters {
      stringParam('WORKFLOW_DOCS_SOURCE', '${WORKSPACE}/workflow', 'Relative Workflow source')
      stringParam('DEIS_COM_SOURCE', '${WORKSPACE}/deis.com', 'Relative Deis.com source')
      stringParam('CONTAINER_ENV', '${GUTENBERG_STAGING_ENV}', 'Environment file with AWS API Keys, S3 Buckets and CloudFront values')
    }
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      cd "${WORKSPACE}/gutenberg"
      make prep build deploy
    '''.stripIndent().trim()
  }
}
