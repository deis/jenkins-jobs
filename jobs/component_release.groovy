def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common/var.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common/var.groovy"))

repos.each { Map repo ->
  if(repo.buildJobs != false) {
    name = "${repo.name}-release"

    job(name) {
      description """
        <ol>
          <li>Watches the ${repo.name} repo for a git tag push. (It can also be triggered manually, supplying a value for TAG.)</li>
          <li>Unless TAG is set (manual trigger), this job only runs off the latest tag.</li>
          <li>The commit at HEAD of tag is then used to locate the release candidate image(s).</li>
          <li>Kicks off downstream e2e job to vet candidate image(s).</li>
          <li>Provided e2e tests pass, retags release candidate(s) with official semver tag in the 'deis' registry orgs.</li>
        </ol>
      """.stripIndent().trim()

      scm {
        git {
          remote {
            github("deis/${repo.name}")
            credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
            refspec('+refs/tags/*:refs/remotes/origin/tags/*')
          }
          branch('*/tags/*')
        }
      }

      logRotator {
        daysToKeep defaults.daysToKeep
      }

      publishers {
        slackNotify(channel: repo.slackChannel, statuses: ['FAILURE'])
      }

      parameters {
        stringParam('TAG', '', 'Specific tag to release')
        stringParam('CLI_VERSION', defaults.cli.release, 'Specific Workflow CLI version to use in downstream e2e run, if applicable')
      }

      triggers {
        githubPush()
      }

      wrappers {
        buildName('${GIT_BRANCH} ${TAG} #${BUILD_NUMBER}')
        timestamps()
        colorizeOutput 'xterm'
        credentialsBinding {
          string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
        }
      }

      steps {
        main = [
          new File("${workspace}/bash/scripts/get_latest_tag.sh").text,
          new File("${workspace}/bash/scripts/locate_release_candidate.sh").text,
        ].join('\n')

        repo.components.each{ Map component ->
          main += """
            #!/usr/bin/env bash

            set -eo pipefail

            tag="\$(get-latest-tag)"
            commit="\$(git checkout "\${tag}" && git rev-parse HEAD)"
            echo "Checked out tag '\${tag}' and will pass commit at HEAD (\${commit}) to downstream job."

            echo "Locating release candidate based on tag commit '\${commit}'..."
            result="\$(locate-release-candidate ${component.name} "\${commit}" "\${tag}")"

            mkdir -p "\$(dirname ${component.envFile})"
            { echo "\${result}"; \
              echo "CLI_VERSION=\${CLI_VERSION}";
              echo "UPSTREAM_SLACK_CHANNEL=\${UPSTREAM_SLACK_CHANNEL}"; } >> ${component.envFile}
          """.stripIndent()
        }

        shell main

        conditionalSteps {
          condition {
            status('SUCCESS', 'SUCCESS')
          }
          steps {
            repo.components.each{ Map component ->
              downstreamParameterized {
                trigger('release-candidate-promote') {
                  block {
                    buildStepFailure('FAILURE')
                    failure('FAILURE')
                    unstable('UNSTABLE')
                  }
                  parameters {
                    propertiesFile(component.envFile)
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
