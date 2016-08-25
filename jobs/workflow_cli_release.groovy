evaluate(new File("${WORKSPACE}/common.groovy"))

repoName="workflow-cli"

job("${repoName}-release") {
  description """
    <li>Watches the ${repoName} repo for a git tag push. (It can also be triggered manually, supplying a value for TAG.)</li>
    <li>The commit at HEAD of tag is then used to locate the release candidate image(s).</li>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/${repoName}")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
        refspec('+refs/tags/*:refs/remotes/origin/tags/*')
      }
      branch('*/tags/*')
    }
  }

  publishers {
    slackNotifications {
      notifyFailure()
      notifyRepeatedFailure()
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  parameters {
    stringParam('TAG', '', 'Specific tag to release')
  }

  triggers {
    githubPush()
  }

  wrappers {
    buildName('${GIT_BRANCH} ${TAG} #${BUILD_NUMBER}')
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      file("GCSKEY", "6561701c-b7b4-4796-83c4-9d87946799e4")
    }
  }

  steps {
    def bucket = "gs://workflow-cli-release"

    def headers  = "-h 'x-goog-meta-ci-job:\${JOB_NAME}' "
        headers += "-h 'x-goog-meta-ci-number:\${BUILD_NUMBER}' "
        headers += "-h 'x-goog-meta-ci-url:\${BUILD_URL}'"

    def script  = "sh -c 'make build-tag "
        script += "&& echo \${GCS_KEY_JSON} | base64 -d - > /tmp/key.json "
        script += "&& gcloud auth activate-service-account -q --key-file /tmp/key.json "
        script += "&& gsutil -mq ${headers} cp -a public-read -r _dist/* ${bucket}'"

    main = new File("${WORKSPACE}/bash/scripts/get_latest_tag.sh").text

    main += """
      #!/usr/bin/env bash

      set -eo pipefail

      tag="\$(get-latest-tag)"
      git_commit="\$(git checkout "\${tag}" && git rev-parse HEAD)"
      revision_image=quay.io/deisci/workflow-cli-dev:"\${git_commit:0:7}"

      # Build and upload artifacts
      docker run -e GCS_KEY_JSON=\"\${GCSKEY}\" --rm "\${revision_image}" ${script}
    """.stripIndent().trim()

    shell main
  }
}
