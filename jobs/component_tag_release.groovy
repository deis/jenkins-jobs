evaluate(new File("${WORKSPACE}/common.groovy"))

repos.each { Map repo ->
  name = "${repo.name}-tag-release"

  job(name) {
    description """
      <ol>
        <li>Watches the ${repo.name} repo for a git tag push. (It can also be triggered manually, supplying a value for TAG.)</li>
        <li>Unless TAG is set (manual trigger), this job only runs off the latest tag.</li>
        <li>The commit at HEAD of tag is then used to locate the release candidate image.</li>
        <li>Kicks off downstream e2e job to vet candidate image.</li>
        <li>Provided e2e tests pass, retags release candidate with official semver tag in the 'deis' registry orgs.</li>
      </ol>
    """.stripIndent().trim()

    scm {
      git {
        remote {
          github("deis/${repo.name}")
          credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
        }
        branch('master')
      }
    }

    logRotator {
      daysToKeep defaults.daysToKeep
    }

    publishers {
      slackNotifications {
        notifyAborted()
        notifyFailure()
        notifySuccess()
        notifyRepeatedFailure()
       }
     }

    parameters {
      stringParam('TAG', '', 'Specific tag to release')
    }

    wrappers {
      timestamps()
      colorizeOutput 'xterm'
    }

    steps {
      shell new File("${WORKSPACE}/bash/scripts/locate_release_candidate.sh").text

      shell '''
        #!/usr/bin/env bash

        set -eo pipefail

        git tag ${TAG}
        git push origin ${TAG}
      '''.stripIndent().trim()

      downstreamParameterized {
        trigger('release-candidate-e2e') {
          block {
            buildStepFailure('FAILURE')
            failure('FAILURE')
            unstable('UNSTABLE')
          }
          parameters {
            propertiesFile('${WORKSPACE}/env.properties')
          }
        }
      }

      // If e2e job results in `SUCCESS`, promote release candidate
      conditionalSteps {
        condition {
          status('SUCCESS', 'SUCCESS')
        }
        steps {
          downstreamParameterized {
            trigger('release-candidate-promote') {
              block {
                buildStepFailure('FAILURE')
                failure('FAILURE')
                unstable('UNSTABLE')
              }
              parameters {
                propertiesFile('${WORKSPACE}/env.properties')
              }
            }
          }
        }
      }
    }
  }
}
