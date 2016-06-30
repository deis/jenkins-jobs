evaluate(new File("${WORKSPACE}/common.groovy"))

import utilities.StatusUpdater

[ [type: 'master'],
  [type: 'pr'],
].each { Map config ->
  isMaster = config.type == 'master'
  isPR = config.type == 'pr'
  name = isMaster ? "e2e-runner" : "e2e-runner-${config.type}"
  dockerPush = isPR ? 'docker-immutable-push' : 'docker-push'
  downstreamJobName = defaults.testJob[config.type]

  job(name) {
    description """
      Watches the e2e-runner repo for a ${config.type} commit
    """.stripIndent().trim()

    scm {
      git {
        remote {
          github("deis/e2e-runner")
          credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
          if (isPR) {
            refspec('+refs/pull/*:refs/remotes/origin/pr/*')
          }
        }
        branch('${sha1}')
      }
    }

    if (isPR) {
      concurrentBuild()
      throttleConcurrentBuilds {
        maxPerNode(defaults.maxBuildsPerNode)
        maxTotal(defaults.maxTotalConcurrentBuilds)
      }
    }

    logRotator {
      daysToKeep defaults.daysToKeep
    }

    if (isPR) { // set up GitHubPullRequest build trigger
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
              triggeredStatus("Triggering e2e-runner build...")
              startedStatus("Starting e2e-runner build...")
              completedStatus('SUCCESS', "Merge with caution! Test job(s) may still be in progress...")
              completedStatus('FAILURE', 'Build/deploy returned failure(s).')
              completedStatus('ERROR', 'Something went wrong.')
            }
          }
        }
      }
    }

    parameters {
      stringParam('DOCKER_USERNAME', 'deisbot', 'Docker Hub account name')
      stringParam('DOCKER_EMAIL', 'dummy-address@deis.com', 'Docker Hub email address')
      stringParam('QUAY_USERNAME', 'deisci+jenkins', 'Quay account name')
      stringParam('QUAY_EMAIL', 'deisci+jenkins@deis.com', 'Quay email address')
      stringParam('sha1', 'master', 'Specific Git SHA to test')
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
        string("GITHUB_ACCESS_TOKEN", "8e11254f-44f3-4ddd-bf98-2cabcb7434cd")
      }
    }

    steps {
      shell 'make docker-test'

      shell """
        #!/usr/bin/env bash
        set -eo pipefail

        docker login -e="\$DOCKER_EMAIL" -u="\$DOCKER_USERNAME" -p="\$DOCKER_PASSWORD"
        DEIS_REGISTRY='' make docker-build ${dockerPush}

        docker login -e="\$QUAY_EMAIL" -u="\$QUAY_USERNAME" -p="\$QUAY_PASSWORD" quay.io
        DEIS_REGISTRY=quay.io/ make docker-build ${dockerPush}

        mkdir -p ${defaults.tmpPath}
        eval \$(make image)
        # if triggered by pull request plugin, use ghprbActualCommit
        export ACTUAL_COMMIT="\${ghprbActualCommit}"
        # if manually triggered, use sha1
        if [ -z "\${ghprbActualCommit}" ]; then
        	export ACTUAL_COMMIT="\${sha1}"
        fi
        echo ACTUAL_COMMIT="\${ACTUAL_COMMIT}" >> ${defaults.envFile}
        echo E2E_RUNNER_IMAGE="\${E2E_RUNNER_IMAGE}" >> ${defaults.envFile}
        echo UPSTREAM_BUILD_URL="\${BUILD_URL}" >> ${defaults.envFile}
        echo COMPONENT_REPO="e2e-runner" >> ${defaults.envFile}
        echo UPSTREAM_SLACK_CHANNEL="testing" >> ${defaults.envFile}
      """.stripIndent().trim()

      downstreamParameterized {
        trigger(downstreamJobName) {
          parameters {
            propertiesFile("${defaults.envFile}")
          }
        }
      }
    }
  }
}
