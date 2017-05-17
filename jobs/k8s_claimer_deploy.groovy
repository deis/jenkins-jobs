def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

slackChannel = '#ops'

job('k8s-claimer-build-cli') {
  description """
  <p>Builds the k8s-claimer CLI and uploads to Azure Blob storage </p>
  <p>
    K8s-Claimer serves as a Kubernetes cluster leaser for running Workflow E2E tests in CI.
  </p>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github('deis/k8s-claimer')
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('master')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    def statusesToNotify = ['SUCCESS', 'FAILURE']
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
                    slack-notify '${slackChannel}' "${buildStatus}"
                  """.stripIndent().trim()
              }
            }
          }
        }
      }
    }
  }

  parameters {
    stringParam('QUAY_USERNAME', 'deis+jenkins', 'Quay account name')
    stringParam('QUAY_EMAIL', 'deis+jenkins@deis.com', 'Quay email address')
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("AZURE_STORAGE_ACCOUNT", defaults.azure.storageAccount)
      string("AZURE_STORAGE_KEY", defaults.azure.storageAccountKeyID)
      string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
    }
  }

  steps {
    shell """
      #!/usr/bin/env bash
      set -eo pipefail

     make bootstrap build-cli
    """.stripIndent().trim()
  }
}



job('k8s-claimer-deploy') {
  description """
  <p>Compiles and deploys <a href="https://github.com/deis/k8s-claimer">k8s-claimer</a>
    to the Deis Workflow staging cluster.
  </p>
  <p>
    K8s-Claimer serves as a Kubernetes cluster leaser for running Workflow E2E tests in CI.
  </p>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github('deis/k8s-claimer')
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('master')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  publishers {
    def statusesToNotify = ['SUCCESS', 'FAILURE']
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
                    slack-notify '${slackChannel}' "${buildStatus}"
                  """.stripIndent().trim()
              }
            }
          }
        }
      }
    }
  }

  parameters {
    stringParam('QUAY_USERNAME', 'deis+jenkins', 'Quay account name')
    stringParam('QUAY_EMAIL', 'deis+jenkins@deis.com', 'Quay email address')
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("KUBECONFIG_BASE64", "aa198b18-3566-438b-bab2-2dd169015567")
      string("K8S_CLAIMER_SSH_KEY", "394d55f4-b9b6-4913-8b22-e874939f90b4")
      string("GOOGLE_CLOUD_ACCOUNT_FILE", "ba7ab317-a820-4e70-9399-a54cf3a59949")
      string("AUTH_TOKEN", "a62d7fe9-5b74-47e3-9aa5-2458ba32da52")
      string("AZURE_SUBSCRIPTION_ID", "1b2376bb-38ed-480b-8bcc-81250ebaa327")
      string("AZURE_CLIENT_ID", "862dbc6f-2fbe-4342-a797-c6433efb6761")
      string("AZURE_CLIENT_SECRET", "3d5c3d60-8648-42f4-8401-7d96e08ca080")
      string("AZURE_TENANT_ID", "528070f3-4799-4c1a-94d6-20a16177487a")
      string("GITHUB_ACCESS_TOKEN", defaults.github.credentialsID)
      string("SLACK_INCOMING_WEBHOOK_URL", defaults.slack.webhookURL)
      string("QUAY_PASSWORD", "8317a529-10f7-40b5-abd4-a42f242f22f0")
    }
  }

  steps {
    shell """
      #!/usr/bin/env bash
      set -eo pipefail
      echo \$KUBECONFIG_BASE64 | base64 --decode > kubeconfig

      download-and-init-helm

      export DEIS_REGISTRY=quay.io/
      docker login -e="\$QUAY_EMAIL" -u="\$QUAY_USERNAME" -p="\$QUAY_PASSWORD" quay.io

      KUBECONFIG=kubeconfig \
      ARGS=config.ssh_key=\${K8S_CLAIMER_SSH_KEY},\
      config.google.account_file=\${GOOGLE_CLOUD_ACCOUNT_FILE},\
      config.google.project_id=deis-e2e-leasable,\
      config.auth_token=\${AUTH_TOKEN},\
      config.namespace=k8sclaimer,\
      config.azure.subscription_id=\${AZURE_SUBSCRIPTION_ID},\
      config.azure.client_id=\${AZURE_CLIENT_ID},\
      config.azure.client_secret=\${AZURE_CLIENT_SECRET},\
      config.azure.tenant_id=\${AZURE_TENANT_ID} make bootstrap build push upgrade
    """.stripIndent().trim()
  }
}
