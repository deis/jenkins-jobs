def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

[ [type: 'master'],
  [type: 'pr'],
].each { Map config ->
  isMaster = config.type == 'master'
  isPR = config.type == 'pr'
  name = isMaster ? "e2e-runner" : "e2e-runner-${config.type}"
  dockerPush = isPR ? 'docker-immutable-push' : 'docker-push'
  downstreamJobName = defaults.testJob.name

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
        string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
      }
    }

    steps {
      main = new File("${workspace}/bash/scripts/get_actual_commit.sh").text

      main += """
        #!/usr/bin/env bash
        set -eo pipefail

        make docker-test

        docker login -e="\$DOCKER_EMAIL" -u="\$DOCKER_USERNAME" -p="\$DOCKER_PASSWORD"
        DEIS_REGISTRY='' make docker-build ${dockerPush}

        docker login -e="\$QUAY_EMAIL" -u="\$QUAY_USERNAME" -p="\$QUAY_PASSWORD" quay.io
        DEIS_REGISTRY=quay.io/ make docker-build ${dockerPush}

        mkdir -p ${defaults.tmpPath}
        eval \$(make image)

        { echo ACTUAL_COMMIT="\$(get-actual-commit e2e-runner \${ghprbActualCommit})"; \
          echo E2E_RUNNER_IMAGE="\${E2E_RUNNER_IMAGE}"; \
          echo UPSTREAM_BUILD_URL="\${BUILD_URL}"; \
          echo COMPONENT_REPO="e2e-runner"; \
          echo UPSTREAM_SLACK_CHANNEL="testing"; } >> ${defaults.envFile}
      """.stripIndent().trim()

      shell main

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
