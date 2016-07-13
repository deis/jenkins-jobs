evaluate(new File("${WORKSPACE}/common.groovy"))

import utilities.StatusUpdater

repos.each { Map repo ->
  [
    // for each repo, create a <repo.name>-master and <repo.name>-pr job
    [type: 'master'],
    [type: 'pr'],
  ].each { Map config ->
    isMaster = config.type == 'master'
    isPR = config.type == 'pr'

    name = isMaster ? repo.name : "${repo.name}-pr"
    downstreamJobName = defaults.testJob[config.type]

    job(name) {
      description """
        <ol>
          <li>Watches the ${repo.name} repo for a ${config.type} commit</li>
          <li>Kicks off downstream ${downstreamJobName} job to vet changes</li>
        </ol>
      """.stripIndent().trim()

      scm {
        git {
          remote {
            github("deis/${repo.name}")
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
                triggeredStatus("Triggering ${repo.name} build/deploy...")
                startedStatus("Starting ${repo.name} build/deploy...")
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
        dockerPush = isPR ? 'docker-immutable-push' : 'docker-push'

        shell new File("${WORKSPACE}/bash/scripts/get_actual_commit.sh").text

        shell """
          #!/usr/bin/env bash

          set -eo pipefail

          # export env vars set in any shell steps prior to this
          export \$(cat "\${WORKSPACE}/env.properties" | xargs)

          make bootstrap || true

          export IMAGE_PREFIX=deisci
          docker login -e="\$DOCKER_EMAIL" -u="\$DOCKER_USERNAME" -p="\$DOCKER_PASSWORD"
          DEIS_REGISTRY='' make docker-build ${dockerPush}
          docker login -e="\$QUAY_EMAIL" -u="\$QUAY_USERNAME" -p="\$QUAY_PASSWORD" quay.io
          DEIS_REGISTRY=quay.io/ make docker-build ${dockerPush}
        """.stripIndent().trim()

        shell new File("${WORKSPACE}/bash/scripts/commit_description_parser.sh").text

        // do not run e2e tests for workflow-manager at this time
        if (repo.name != 'workflow-manager') {
          conditionalSteps {
            condition {
              shell new File("${WORKSPACE}/bash/scripts/skip_e2e_check.sh").text
            }
            steps {
              downstreamParameterized {
                trigger(downstreamJobName) {
                  parameters {
                    propertiesFile('${WORKSPACE}/env.properties')
                    predefinedProps([
                      "${repo.commitEnvVar}": '${GIT_COMMIT}',
                      'UPSTREAM_BUILD_URL': '${BUILD_URL}',
                      'UPSTREAM_SLACK_CHANNEL': "${repo.slackChannel}",
                      'COMPONENT_REPO': "${repo.name}",
                    ])
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
