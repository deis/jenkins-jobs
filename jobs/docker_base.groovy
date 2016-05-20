evaluate(new File("${WORKSPACE}/common.groovy"))

import utilities.StatusUpdater


// for each repo, create a <dir.name>-master and <dir.name>-pr job
[
[type: 'master', branch: 'master'],
[type: 'pr', branch: '${sha1}'],
[type: 'tag', branch: 'master'],
].each { Map config ->
  isMaster = config.type == 'master'
  isPR = config.type == 'pr'
  isTag = config.type == 'tag'
  if (isMaster) {
    name = "docker-base"
  } else if (isPR) {
    name = "docker-base-pr"
  } else {
    name = "docker-base-tag"
  }

  job(name) {
    description """
      <ol>
        <li>Watches the docker-base repo for a ${config.type} commit</li>
      </ol>
    """.stripIndent().trim()

    scm {
      git {
        remote {
          github("deis/docker-base")
          credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
          if (isPR) {
            refspec('+refs/pull/*:refs/remotes/origin/pr/*')
          } else if (isTag) {
            refspec('+refs/tags/*:refs/remotes/origin/tags/*')
          }
          branch(config.branch)
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
              context("ci/jenkins/pr")
              triggeredStatus("Triggering docker-base build/deploy...")
              startedStatus("Starting docker-base build/deploy...")
              completedStatus('SUCCESS', "Build/deploy successful!")
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
      prefix = isPR ? 'deisci' : 'deis'
      shell """
        #!/usr/bin/env bash
        set -eo pipefail

        export IMAGE_PREFIX=${prefix}
        docker login -e="\$DOCKER_EMAIL" -u="\$DOCKER_USERNAME" -p="\$DOCKER_PASSWORD"
        DEIS_REGISTRY='' make build push
        docker login -e="\$QUAY_EMAIL" -u="\$QUAY_USERNAME" -p="\$QUAY_PASSWORD" quay.io
        DEIS_REGISTRY=quay.io/ make build push
      """.stripIndent().trim()
    }
  }
}
