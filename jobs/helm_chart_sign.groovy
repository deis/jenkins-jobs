def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

job("helm-chart-sign") {
  description "Signs a Deis Helm chart"

  publishers {
    wsCleanup() // Scrub workspace clean after build

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
    nodeParam('SIGNATORY_NODE'){
      description('select signatory node')
      defaultNodes(['node7-ec2'])
      allowedNodes(['node7-ec2'])
    }
    stringParam('CHART', '', 'Name of chart to be signed')
    stringParam('VERSION', '', 'Specific version of the chart to be signed')
    stringParam('CHART_REPO', '', 'Name of chart repo if other than CHART')
    stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
    stringParam('UPSTREAM_SLACK_CHANNEL', defaults.slack.channel, "Upstream Slack channel")
  }

  wrappers {
    buildName('${CHART} ${VERSION} #${BUILD_NUMBER}')
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("AWS_ACCESS_KEY_ID", '57e64439-4521-4a4f-9315-eac10ecdea75')
      string("AWS_SECRET_ACCESS_KEY", '313da896-1579-41fa-9c70-c6b13d938e9c')
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
      string("SIGNING_KEY_PASSPHRASE", '3963b12b-bad3-429b-b1e5-e047a159bf02')
    }
  }

  steps {
    shell new File("${workspace}/bash/scripts/helm_chart_actions.sh").text +
      '''
        #!/usr/bin/env bash

        set -eo pipefail

        download-and-init-helm

        sign-helm-chart "${CHART}" "${VERSION}"

        helm verify "${CHART}-${VERSION}".tgz

        upload-signed-chart "${CHART}-${VERSION}" "${CHART_REPO:-${CHART}}"
      '''.stripIndent().trim()

    conditionalSteps {
      condition {
        status('SUCCESS', 'SUCCESS')
      }
      steps {
        downstreamParameterized {
          trigger("helm-chart-verify") {
            block {
              buildStepFailure('FAILURE')
              failure('FAILURE')
              unstable('UNSTABLE')
            }
            parameters {
              predefinedProps([
                'CHART': '${CHART}',
                'VERSION': '${VERSION}',
                'CHART_REPO': '${CHART_REPO}',
              ])
            }
          }
        }
      }
    }
  }
}

job("helm-chart-verify") {
  description "Verifies a signed Deis Helm chart"

  publishers {
    wsCleanup() // Scrub workspace clean after build

    postBuildScripts {
      onlyIfBuildSucceeds(false)
      steps {
        defaults.statusesToNotify.each { buildStatus ->
          conditionalSteps {
            condition {
             status(buildStatus, buildStatus)
              steps {
                shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                  "slack-notify '#testing' '${buildStatus}'"
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
    stringParam('VERSION', '', 'Specific version of the chart to be signed')
    stringParam('CHART_REPO', '', 'Name of chart repo if other than CHART')
    stringParam('HELM_VERSION', defaults.helm.version, 'Version of Helm to download/use')
  }

  wrappers {
    buildName('${CHART} ${VERSION} #${BUILD_NUMBER}')
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

        chart="${CHART}"
        chart_repo="${CHART_REPO:-${chart}}"

        download-and-init-helm

        # fetch key from keyserver
        gpg --keyserver pgp.mit.edu --recv-keys 1D6A97D0

        helm repo add "${chart}" https://charts.deis.com/"${chart_repo}"
        helm fetch --verify "${chart_repo}"/"${chart}" --version "${VERSION}"
      '''.stripIndent().trim()
  }
}
