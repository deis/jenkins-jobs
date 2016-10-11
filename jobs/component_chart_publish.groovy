def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

repos.each { Map repo ->
  if (repo.chart) {
    job("${repo.chart}-chart-publish") {
      description "Publishes a new ${repo.name} chart release."

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

      publishers {
        def statusesToNotify = ['FAILURE']
        postBuildScripts {
          onlyIfBuildSucceeds(false)
          steps {
            statusesToNotify.each { buildStatus ->
              conditionalSteps {
                condition {
                 status(buildStatus, buildStatus)
                  steps {
                    shell new File("${workspace}/bash/scripts/slack_notify.sh").text +
                      """
                        slack-notify "${repo.slackChannel}" "${buildStatus}"
                      """.stripIndent().trim()
                  }
                }
              }
            }
          }
        }
      }

      logRotator {
        daysToKeep defaults.daysToKeep
      }

      parameters {
        stringParam('RELEASE_TAG', '', 'Release tag')
        stringParam('HELM_VERSION', 'v2.0.0-alpha.5', 'Version of Helm to download/use')
      }

      wrappers {
        buildName('${RELEASE_TAG} #${BUILD_NUMBER}')
        timestamps()
        colorizeOutput 'xterm'
        credentialsBinding {
          string("AWS_ACCESS_KEY_ID", '57e64439-4521-4a4f-9315-eac10ecdea75')
          string("AWS_SECRET_ACCESS_KEY", '313da896-1579-41fa-9c70-c6b13d938e9c')
          string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
        }
      }

      steps {
        shell """
          #!/usr/bin/env bash

          set -eo pipefail
          set -x

          # check out RELEASE_TAG tag
          commit="\$(git checkout "\${RELEASE_TAG}" && git rev-parse HEAD)"

          if [ -d charts ]; then
            cd charts

            ## change chart values
            # update the chart version to RELEASE_TAG
            perl -i -0pe "s/<Will be populated by the ci before publishing the chart>/\${RELEASE_TAG}/g" ${repo.chart}/Chart.yaml
            # update all org values to "deis"
            perl -i -0pe 's/"deisci"/"deis"/g' ${repo.chart}/values.yaml
            # update the image pull policy to "IfNotPresent"
            perl -i -0pe 's/"Always"/"IfNotPresent"/g' ${repo.chart}/values.yaml
            # update the dockerTag value to RELEASE_TAG
            perl -i -0pe "s/canary/\${RELEASE_TAG}/g" ${repo.chart}/values.yaml

            # download helm and init
            export HELM_OS=linux \
              && wget http://storage.googleapis.com/kubernetes-helm/helm-"\${HELM_VERSION}"-"\${HELM_OS}"-amd64.tar.gz \
              && tar -zxvf helm-"\${HELM_VERSION}"-"\${HELM_OS}"-amd64.tar.gz \
              && export PATH="\${HELM_OS}-amd64:\${PATH}" \
              && helm init -c

            # package release chart
            helm package ${repo.chart}

            # download index file from aws s3 bucket
            aws s3 cp s3://helm-charts/${repo.chart}/index.yaml .

            # update index file
            helm repo index . --url https://charts.deis.com/${repo.chart}

            # push packaged chart and updated index file to aws s3 bucket
            aws s3 cp ${repo.chart}-\${RELEASE_TAG}.tgz s3://helm-charts/${repo.chart}/ \
              && aws s3 cp index.yaml s3://helm-charts/${repo.chart}/index.yaml
          else
            echo "No 'charts' directory found at project level; nothing to publish."
          fi
        """.stripIndent().trim()
      }
    }
  }
}
