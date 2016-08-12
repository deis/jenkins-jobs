evaluate(new File("${WORKSPACE}/common.groovy"))

job('release-candidate-promote') {
  description """
    Promotes a release candidate image by retagging with the official semver tag to the production 'deis' registry org on an upstream e2e success
  """.stripIndent().trim()


  concurrentBuild()

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    slackNotifications {
      notifyFailure()
      notifySuccess()
    }
  }

  parameters {
    stringParam('DOCKER_USERNAME', 'deisbot', 'Docker Hub account name')
    stringParam('DOCKER_EMAIL', 'dummy-address@deis.com', 'Docker Hub email address')
    stringParam('QUAY_USERNAME', 'deis+jenkins', 'Quay account name')
    stringParam('QUAY_EMAIL', 'deis+jenkins@deis.com', 'Quay email address')
    stringParam('COMPONENT_NAME', '', 'Component name')
    stringParam('COMPONENT_SHA', '', 'Commit sha used for candidate image tag')
    stringParam('RELEASE_TAG', '', 'Release tag value for retagging candidate image')
  }

  wrappers {
    buildName('${COMPONENT_NAME} release #${BUILD_NUMBER}')
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("DOCKER_PASSWORD", "0d1f268f-407d-4cd9-a3c2-0f9671df0104")
      string("QUAY_PASSWORD", "8317a529-10f7-40b5-abd4-a42f242f22f0")
    }
  }

  steps {
    shell new File("${WORKSPACE}/bash/scripts/retag_release_candidate.sh").text
  }
}
