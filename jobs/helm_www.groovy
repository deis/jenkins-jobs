def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

repo_name = 'helm-www'
downstreamJobName = 'helm-www-deploy'
slackChannel = '#marketing'

job("helm-www-merge") {
  description """
    <ol>
      <li>Watches the <a href="https://github.com/helm/${repo_name}">${repo_name}</a> repo for a commit to master</li>
      <li>Kicks off downstream ${downstreamJobName} job to deploy</li>
    </ol>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("helm/helm-www")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
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

    downstream("${downstreamJobName}", 'UNSTABLE')
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
  }

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      make build build-image
    '''.stripIndent().trim()
  }
}

job("helm-www-pr") {
  description """
    <ol>
      <li>Watches the ${repo_name} repo_name for pull requests</li>
    </ol>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("helm/helm-www")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
        refspec('+refs/pull/*:refs/remotes/origin/pr/*')
      }
      branch('${sha1}')
    }
  }

  concurrentBuild()
  throttleConcurrentBuilds {
    maxPerNode(defaults.maxBuildsPerNode)
    maxTotal(defaults.maxTotalConcurrentBuilds)
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  triggers {
    pullRequest {
      admin('deis-admin')
      cron('H/5 * * * *')
      useGitHubHooks()
      triggerPhrase('OK to test')
      orgWhitelist(['deis'])
      allowMembersOfWhitelistedOrgsAsAdmin()
      // this plugin will update PR status no matter what,
      // so until we fix this, here are our default messages:
      extensions {
        commitStatus {
          context('ci/jenkins/pr')
          triggeredStatus("Triggering ${repo_name} tests...")
          startedStatus("Starting ${repo_name} tests...")
          completedStatus("SUCCESS", "Merge with caution! Test job(s) may still be in progress...")
          completedStatus("FAILURE", "Tests returned failure(s).")
          completedStatus("ERROR", "Something went wrong.")
        }
      }
    }
  }

  parameters {
    stringParam('sha1', '${BRANCH}', 'Specific Git SHA to test')
  }

  triggers {
    githubPush()
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
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

  steps {
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      make build build-image
    '''.stripIndent().trim()
  }
}
