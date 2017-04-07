def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

name = 'deis-com-deploy'
slackChannel = '#marketing'

job(name) {
  description """
    <ol>
      <li>Compiles and deploys <a href="https://deis.com">deis.com</a></li>
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
     branch('*/tags/*')
   }
   git {
     remote {
         github('deis/deis.com')
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
    def statusesToNotify = ['SUCCESS', 'FAILURE']
    postBuildScripts {
      onlyIfBuildSucceeds(false)
      steps {
        statusesToNotify.each { buildStatus ->
          conditionalSteps {
            condition {
             status(buildStatus, buildStatus)
              steps {
                shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                  """
                    slack-notify '${slackChannel}' "${buildStatus}"
                  """.stripIndent().trim()
              }
            }
          }
        }
      }
    }
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      file('GUTENBERG_STAGING_ENV', '7d29c809-9480-47ae-91aa-a25f43e58897')
      file('GUTENBERG_PROD_ENV', '91cd521d-e2bb-452d-9abd-863e00ff1e12')
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
    parameters {
      stringParam('WORKFLOW_DOCS_SOURCE', '${WORKSPACE}/workflow', 'Relative Workflow source')
      stringParam('DEIS_COM_SOURCE', '${WORKSPACE}/deis.com', 'Relative Deis.com source')
      stringParam('CONTAINER_ENV', '${GUTENBERG_PROD_ENV}', 'Environment file with AWS API Keys, S3 Buckets and CloudFront values')
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
