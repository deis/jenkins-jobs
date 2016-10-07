def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common/var.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common/var.groovy"))

job('release-candidate-promote') {
  description """
    Promotes a release candidate image by retagging with the official semver tag to the production 'deis' registry org on an upstream e2e success
  """.stripIndent().trim()


  concurrentBuild()

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    slackNotify(channel: '${UPSTREAM_SLACK_CHANNEL}', statuses: ['FAILURE'])
  }

  parameters {
    stringParam('DOCKER_USERNAME', 'deisbot', 'Docker Hub account name')
    stringParam('DOCKER_EMAIL', 'dummy-address@deis.com', 'Docker Hub email address')
    stringParam('QUAY_USERNAME', 'deis+jenkins', 'Quay account name')
    stringParam('QUAY_EMAIL', 'deis+jenkins@deis.com', 'Quay email address')
    stringParam('COMPONENT_NAME', '', 'Component name')
    stringParam('COMPONENT_SHA', '', 'Commit sha used for candidate image tag')
    stringParam('RELEASE_TAG', '', 'Release tag value for retagging candidate image')
    stringParam('UPSTREAM_SLACK_CHANNEL', defaults.slack.channel, 'Upstream/Component Slack channel')
  }

  wrappers {
    buildName('${COMPONENT_NAME} release #${BUILD_NUMBER}')
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("DOCKER_PASSWORD", "0d1f268f-407d-4cd9-a3c2-0f9671df0104")
      string("QUAY_PASSWORD", "8317a529-10f7-40b5-abd4-a42f242f22f0")
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
  }

  steps {
    shell new File("${workspace}/bash/scripts/retag_release_candidate.sh").text

    downstreamParameterized {
      trigger('component-release-publish') {
        parameters {
          predefinedProps([
            'COMPONENT': '${COMPONENT_NAME}',
            'RELEASE': '${RELEASE_TAG}',
          ])
        }
      }
    }
  }
}
