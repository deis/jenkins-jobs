def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

job("helm-chart-verify") {
  description "Verifies a signed Deis Helm chart"

  publishers {
    postBuildScripts {
      onlyIfBuildSucceeds(false)
      steps {
        defaults.statusesToNotify.each { buildStatus ->
          conditionalSteps {
            condition {
             status(buildStatus, buildStatus)
              steps {
                shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                  "slack-notify \${UPSTREAM_SLACK_CHANNEL} '${buildStatus}'"
              }
            }
          }
        }
      }
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  parameters {
    stringParam('CHART', '', 'Name of chart to be signed')
    choiceParam('CHART_REPO_TYPE', ['dev', 'staging', 'production'], 'Type of chart repo for publishing (default: dev)')
    stringParam('RELEASE_TAG', '', 'Specific version of the chart to be verified')
    stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
    stringParam('UPSTREAM_SLACK_CHANNEL', defaults.slack.channel, "Upstream Slack channel")
  }

  wrappers {
    preBuildCleanup() // Scrub workspace clean before build

    buildName('${CHART} ${RELEASE_TAG} #${BUILD_NUMBER}')
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
  }

  steps {
    shell new File("${workspace}/bash/scripts/helm_chart_actions.sh").text +
      '''
        #!/usr/bin/env bash

        set -eo pipefail

        CHART_REPO="$(echo "${CHART}-${CHART_REPO_TYPE}" | sed -e 's/-production//g')"

        download-and-init-helm

        # fetch key from keyserver
        gpg --keyserver pgp.mit.edu --recv-keys 1D6A97D0

        # coerce gpg into old keyring format
        mkdir -p "${HOME}/.gnupg"
        touch "${HOME}/.gnupg/pubring.gpg"

        helm repo add "${CHART}" https://charts.deis.com/"${CHART_REPO}"
        helm fetch --verify "${CHART_REPO}"/"${CHART}" --version "${RELEASE_TAG}"
      '''.stripIndent().trim()
  }
}
