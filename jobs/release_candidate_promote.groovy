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
    shell '''
      #!/usr/bin/env bash

      set -eo pipefail

      image_name="deis/${COMPONENT_NAME}"

      # release to dockerhub
      candidate_image="${image_name}":git-"${COMPONENT_SHA:0:7}"
      released_image="${image_name}":"${RELEASE_TAG}"

      echo "Retagging ${candidate_image} to ${released_image}"

      docker login -e="${DOCKER_EMAIL}" -u="${DOCKER_USERNAME}" -p="${DOCKER_PASSWORD}"
      docker pull "${candidate_image}"
      docker tag "${candidate_image}" "${released_image}"
      docker push "${released_image}"

      # release to quay.io
      candidate_image=quay.io/"${candidate_image}"
      released_image=quay.io/"${released_image}"

      echo "Retagging ${candidate_image} to ${released_image}"

      docker login -e="${QUAY_EMAIL}" -u="${QUAY_USERNAME}" -p="${QUAY_PASSWORD}" quay.io
      docker pull "${candidate_image}"
      docker tag "${candidate_image}" "${released_image}"
      docker push "${released_image}"
    '''.stripIndent().trim()
  }
}
