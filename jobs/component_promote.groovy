evaluate(new File("${WORKSPACE}/common.groovy"))

job('component-promote') {
  description """
    Promotes a component image to the production 'deis' registry org on e2e success after a merge to master
  """.stripIndent().trim()


  concurrentBuild()
  throttleConcurrentBuilds {
    maxPerNode(defaults.maxBuildsPerNode)
    maxTotal(defaults.maxTotalConcurrentBuilds)
  }

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
    stringParam('COMPONENT_SHA', '', 'Commit sha used for image tag')
  }

  wrappers {
    buildName('${COMPONENT_NAME} promote #${BUILD_NUMBER}')
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

      image_name_and_tag="${COMPONENT_NAME}:git-${COMPONENT_SHA:0:7}"

      # promote to dockerhub
      original_image=deisci/"${image_name_and_tag}"
      promoted_image=deis/"${image_name_and_tag}"

      echo "Promoting ${original_image} to ${promoted_image}"

      docker login -e="${DOCKER_EMAIL}" -u="${DOCKER_USERNAME}" -p="${DOCKER_PASSWORD}"
      docker pull "${original_image}"
      docker tag "${original_image}" "${promoted_image}"
      docker push "${promoted_image}"

      # promote to quay.io
      original_image=quay.io/"${original_image}"
      promoted_image=quay.io/"${promoted_image}"

      echo "Promoting ${original_image} to ${promoted_image}"

      docker login -e="${QUAY_EMAIL}" -u="${QUAY_USERNAME}" -p="${QUAY_PASSWORD}" quay.io
      docker pull "${original_image}"
      docker tag "${original_image}" "${promoted_image}"
      docker push "${promoted_image}"
    '''.stripIndent().trim()
  }
}
