def workspace = new File(".").getAbsolutePath()
if (!new File("${workspace}/common.groovy").canRead()) { workspace = "${WORKSPACE}"}
evaluate(new File("${workspace}/common.groovy"))

job('component-release-publish') {
  description """
    Publishes the component release details to the workflow-manager-api.
  """.stripIndent().trim()

  scm {
    git {
      remote {
        github("deis/workflow-manager-api-publish")
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
      notifyFailure()
      notifyRepeatedFailure()
    }
    git {
      pushOnlyIfSuccess()
      branch('origin', 'master')
    }
  }

  parameters {
    stringParam('COMPONENT', '', "Component name, e.g. 'controller'")
    stringParam('RELEASE', '', "Release string, e.g. 'v1.2.3'")
  }

  wrappers {
    buildName('${COMPONENT} ${RELEASE} #${BUILD_NUMBER}')
    timestamps()
    colorizeOutput 'xterm'
  }

  steps {
    shell 'make publish-release'

    shell '''
      git add versions/${COMPONENT}/${RELEASE}.json
      git commit versions/${COMPONENT}/${RELEASE}.json -m "feat(versions): add ${COMPONENT} ${RELEASE}"
    '''.stripIndent().trim()
  }
}
