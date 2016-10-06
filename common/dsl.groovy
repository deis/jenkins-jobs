def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common/dsl.groovy").canRead()) { workspace = "${WORKSPACE}"}

// Common/Re-usable DSL functions

slackNotify = { Map notification ->
  return {
    postBuildScripts {
      onlyIfBuildSucceeds(false)
      steps {
        notification.statuses.each { String buildStatus ->
          conditionalSteps {
            condition {
              status(buildStatus, buildStatus)
              steps {
                shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                  "slack-notify ${notification.channel} ${buildStatus} ${notification.message}"
              }
            }
          }
        }
      }
    }
  }
}
