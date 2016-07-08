evaluate(new File("${WORKSPACE}/common.groovy"))

name = 'milestone-watcher'

job(name) {
  description """
    <p>Cron job watching latest GitHub milestone in the deis/workflow repo
    to determine whether or not to update the appropriate value in deis/jenkins-jobs</p>
  """.stripIndent().trim()

  scm {
    git {
      remote {
        //TODO: change to deis; master branch
        github("vdice/jenkins-jobs")
        credentials('597819a0-b0b9-4974-a79b-3a5c2322606d')
      }
      branch('milestone-watcher')
    }
  }

  logRotator {
    daysToKeep defaults.daysToKeep
  }

  triggers {
    cron('@daily')
  }

  wrappers {
    timestamps()
    colorizeOutput 'xterm'
    credentialsBinding {
      string("GITHUB_ACCESS_TOKEN", "8e11254f-44f3-4ddd-bf98-2cabcb7434cd")
    }
  }

  steps {
    shell new File("${WORKSPACE}/bash/scripts/milestone_watcher.sh").text
  }
}
