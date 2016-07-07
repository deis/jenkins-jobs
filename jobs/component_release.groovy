evaluate(new File("${WORKSPACE}/common.groovy"))

repos.each { Map repo ->

  job("${repo.name}-release") {
    description """
      <li>Watches the ${repo.name} repo for commits on a given release-\${RELEASE} branch</li>
      <li>to build and deploy images using said commits</li>
    """.stripIndent().trim()

    scm {
      git {
        remote {
          github("deis/${repo.name}")
          credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
        }
        branch('release-${RELEASE}')
      }
    }

    concurrentBuild()

    logRotator {
      daysToKeep defaults.daysToKeep
    }

    parameters {
      stringParam('DOCKER_USERNAME', 'deisbot', 'Docker Hub account name')
      stringParam('DOCKER_EMAIL', 'dummy-address@deis.com', 'Docker Hub email address')
      stringParam('QUAY_USERNAME', 'deisci+jenkins', 'Quay account name')
      stringParam('QUAY_EMAIL', 'deisci+jenkins@deis.com', 'Quay email address')
      stringParam('RELEASE', defaults.workflow.release, 'Release to use for branch checkout')
      booleanParam('RUN_E2E', false, 'check to run downstream release e2e job')
    }

    triggers {
      githubPush()
    }

    wrappers {
      timestamps()
      colorizeOutput 'xterm'
      credentialsBinding {
        string("DOCKER_PASSWORD", "0d1f268f-407d-4cd9-a3c2-0f9671df0104")
        string("QUAY_PASSWORD", "c67dc0a1-c8c4-4568-a73d-53ad8530ceeb")
      }
    }

    steps {
      shell new File("${WORKSPACE}/bash/scripts/check_release_branch.sh").text

      shell '''
        #!/usr/bin/env bash

        set -eo pipefail

        make bootstrap || true

        export IMAGE_PREFIX=deisci
        docker login -e="${DOCKER_EMAIL}" -u="${DOCKER_USERNAME}" -p="${DOCKER_PASSWORD}"
        DEIS_REGISTRY='' make docker-build docker-immutable-push
        docker login -e="${QUAY_EMAIL}" -u="${QUAY_USERNAME}" -p="${QUAY_PASSWORD}" quay.io
        DEIS_REGISTRY=quay.io/ make docker-build docker-immutable-push
      '''.stripIndent().trim()


      conditionalSteps {
        condition {
          booleanCondition('RUN_E2E')
        }
        steps {
          downstreamParameterized {
            trigger(defaults.testJob['release']) {
              parameters {
                predefinedProps([
                  "WORKFLOW_BRANCH": 'release-${RELEASE}',
                  "WORKFLOW_E2E_BRANCH": 'release-${RELEASE}',
                  "RELEASE": '${RELEASE}'
                ])
              }
            }
          }
        }
      }
    }
  }
}
