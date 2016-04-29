import utilities.StatusUpdater

dirs = [
  [name: 'grafana', slackChannel: 'monitor'],
  [name: 'influxdb', slackChannel: 'monitor'],
  [name: 'telegraf', slackChannel: 'monitor'],
]

dirs.each { Map dir ->
  dir.commitEnvVar = "${dir.name.toUpperCase().replaceAll('-', '_')}_SHA"
}

dirs.each { Map dir ->
  [
    // for each repo, create a <dir.name>-master and <dir.name>-pr job
    [type: 'master', branch: 'master'],
    [type: 'pr', branch: '${sha1}'],
  ].each { Map config ->
    isMaster = config.type == 'master'
    isPR = config.type == 'pr'

    name = isMaster ? dir.name : "${dir.name}-pr"

    job(name) {
      description """
        <ol>
          <li>Watches the ${dir.name} subdirectory for a ${config.type} commit</li>
        </ol>
      """.stripIndent().trim()

      scm {
        git {
          remote {
            github("deis/monitor")
            credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
            if (isPR) {
              refspec('+refs/pull/*:refs/remotes/origin/pr/*')
            }
            branch(config.branch)
          }
          configure { gitScm ->
            gitScm / 'extensions' << 'hudson.plugins.git.extensions.impl.PathRestriction' {
              includedRegions(dir.name)
            }
          }
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
                triggeredStatus("Triggering ${dir.name} build/deploy...")
                startedStatus("Starting ${dir.name} build/deploy...")
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
        dockerPush = isPR ? 'docker-immutable-push' : 'docker-push'
        shell """
          #!/usr/bin/env bash

          set -eo pipefail

          make bootstrap || true

          export IMAGE_PREFIX=deisci
          docker login -e="\$DOCKER_EMAIL" -u="\$DOCKER_USERNAME" -p="\$DOCKER_PASSWORD"
          DEIS_REGISTRY='' make docker-build ${dockerPush}
          docker login -e="\$QUAY_EMAIL" -u="\$QUAY_USERNAME" -p="\$QUAY_PASSWORD" quay.io
          DEIS_REGISTRY=quay.io/ make docker-build ${dockerPush}

          # if triggered by pull request plugin, use ghprbActualCommit
          export ACTUAL_COMMIT="\${ghprbActualCommit}"
          # if manually triggered, use sha1
          if [ -z "\${ghprbActualCommit}" ]; then
            export ACTUAL_COMMIT="\${sha1}"
          fi
          echo ACTUAL_COMMIT="\${ACTUAL_COMMIT}" > \${WORKSPACE}/env.properties
        """.stripIndent().trim()
      }
    }
  }
}
