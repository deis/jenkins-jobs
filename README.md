
|![](https://upload.wikimedia.org/wikipedia/commons/thumb/1/17/Warning.svg/156px-Warning.svg.png) | Deis Workflow will soon no longer be maintained.<br />Please [read the announcement](https://deis.com/blog/2017/deis-workflow-final-release/) for more detail. |
|---:|---|
| 09/07/2017 | Deis Workflow [v2.18][] final release before entering maintenance mode |
| 03/01/2018 | End of Workflow maintenance: critical patches no longer merged |

# Deis Workflow Jenkins Jobs

Deis (pronounced DAY-iss) Workflow is an open source Platform as a Service (PaaS) that adds a developer-friendly layer to any [Kubernetes](http://kubernetes.io) cluster, making it easy to deploy and manage applications on your own servers.

For more information about the Deis Workflow, please visit the main project page at https://github.com/deis/workflow.

# About

This repository serves as a central location for [Deis Workflow Jenkins jobs](https://ci.deis.io) represented in [Jenkins Job DSL](https://github.com/jenkinsci/job-dsl-plugin).

## Resources

The definitive wiki resource describing all available Jenkins Job DSL API items can be found in the [Jenkins Job DSL Plugin API](https://jenkinsci.github.io/job-dsl-plugin/).  See also the following helpful [overview](https://youtu.be/SSK_JaBacE0) of the Job DSL Plugin for those new to this project.

All jobs are written in [Groovy](http://www.groovy-lang.org/documentation.html) which runs on the Java platform hosted on Jenkins.

## Directory Structure

The DSL representation of a given job is currently placed in the `jobs` directory.  As much as possible, the actual shell logic that gets executed during a job's runtime is placed in the `bash/scripts` directory.  Correlating [Bats][bats] tests for these scripts are located in `bash/tests`.

## Working with Groovy

For debugging general Groovy code, one may run `make groovy-console` provided one has a Java JDK or JRE installed (_version 7 or higher; see [Gradle Prerequisites][gradle-prereqs]_).

Alternatively, one may use the handy-dandy [Groovy Web Console](https://groovyconsole.appspot.com/).

## Testing

There are containerized and non-containerized targets for all test-related `make` tasks.

#### bash

For testing bash script changes/additions, one may run `make (docker-)test-style` to run the [shellcheck][shellcheck] static analysis tool against script syntax and `make (docker-)test-scripts` to run the suite of `bats` tests located in `bash/tests`.
_(Note: if running the non-containerized target(s), [bats][bats] and [shellcheck][shellcheck] are prerequisites)_

#### DSL/Groovy

For testing that the jobs' DSL/Groovy logic parses correctly, one may run `make (docker-)test-dsl`.  (There is also a `docker-test-dsl-quick` target that starts the stopped `gradle-test` container if it exists, capitalizing on cached dependencies.)
_(Note: if running the non-containerized target(s), the [Gradle Prerequisites][gradle-prereqs] are needed)_

Regardless of target, test results may be viewed in a web browser after they finish via `make open-test-results`.

**How it works:** Each job is processed against the versions of Jenkins and the Job DSL Plugin as defined in `gradle.properties`, as well as the versions of necessary plugins required by the job(s), as declared in the `dependencies` block of `build.gradle`.

Therefore, if all jobs parse without failures, one can be reasonably confident they will build successfully on the live Jenkins instance, provided the aforementioned dependency versions are aligned.

This [Gradle](https://gradle.org/)-based test harness was set up using a slimmed-down/modified version of the [Jenkins Job DSL Gradle Example](https://github.com/sheehan/job-dsl-gradle-example).

## Upgrading Jenkins plugins

Since a test harness exists for checking that the job DSL compiles, one can also check that an upgraded/added plugin plays nicely with existing/new jobs.

To do so, one would update/add the appropriate plugin in `build.gradle` and then run `make (docker-)test-dsl` to make sure compilation is unaffected.  This is meeant to provide a higher level confidence before actually upgrading/adding plugin(s) on the live Jenkins instance.


## Flow

As a standard practice, the initial job will describe the pipeline, in the form of `downstreamParameterized` steps
that follow the main steps of the job itself.  See the [Workflow component job](https://github.com/deis/jenkins-jobs/blob/master/jobs/component_jobs.groovy) as an example.  Most of the downstream jobs are then set up to only execute their specific job logic, and not add further downstream dependencies that might be different from what the initial job specifies.

(The pipelines below can also be found in their original `.monopic` format if needing to change/update.)

### Component PR/Master Pipeline
```
                                          Component Pipeline start
                                                (PR, master)

                                        ┌───────────────────────────┐                                         ┌─────────────────────────────────┐
                                        │                           │                                         │     Commit type: REPO_TYPE      │
                                        │                           │ - check out source code                 │                                 │██
                                        │    component Docker image │ - build and push Docker                 │        PR commit: 'pr',         │██
               ┌─────────────────────   │       build/deploy job    │          image          ─────┐          │     Merge to master: 'dev'      │██
               │                        │                           │ - on success, initiate       │          │ Official release: 'production'  │██
               │                        │                           │     downstream job           │          │                                 │██
               │                        │                           │                              │          │ Staging for release: 'staging'  │██
               ▼                        └───────────────────────────┘                              │          │         (only Workflow)         │██
                                                                                                   ▼          └─────────────────────────────────┘██
                                                                                                                ███████████████████████████████████
       if change to chart                                                                                       ███████████████████████████████████
    ('charts' subdirectory)                                                         if NO change in to chart


   ┌───────────────────────────┐                                                  ┌───────────────────────────┐
   │                           │                                                  │                           │   - set GitHub status to 'Pending'
   │                           │                                                  │                           │                (if PR)
   │     component chart       │  - package chart from specified                  │                           │  - run end-to-end tests against test
   │         publish           │              commit                              │     workflow chart e2e    │            image supplied
   │                           │ - publish to specified chart repo                │                           │   - report result to Slack (as well
   │                           │                                                  │                           │           as GitHub if PR)
   │                           │                                                  │                           │
   └───────────────────────────┘                                                  └───────────────────────────┘
               │                                                                                │
               │                                                                                │
               ▼                                                                                ▼
   ┌───────────────────────────┐                                                  ┌───────────────────────────┐
   │                           │                                                  │                           │
   │                           │      - package workflow chart                    │                           │          (if Master merge)
   │     workflow chart        │ (injecting specific component chart              │                           │ - pull image from dev repo/registry
   │         publish           │        version if supplied)                      │     component promote     │ - push/promote image to production
   │                           │  - publish to specified chart repo               │                           │            repo/registry
   │                           │                                                  │                           │
   │                           │                                                  │                           │
   └───────────────────────────┘                                                  └───────────────────────────┘
               │
               │
               ▼
   ┌───────────────────────────┐
   │                           │  - install workflow and workflow-e2e
   │                           │  charts via helper tools (e2e-runner,
   │                           │  k8s-claimer)
   │    workflow chart e2e     │  - (can set chart values for both
   │                           │  depending on env vars)
   │                           │  - report result to Slack (as well as
   │                           │  GitHub if PR)
   └───────────────────────────┘
```

### Component Release Pipeline
```



   Component Release Pipeline
              start

  ┌───────────────────────────┐
  │                           │
  │                           │ - triggered by `v1.2.3` git tag
  │                           │             webhook
  │     component-release     │
  │                           │ - locate release candidate image
  │                           │     associated with git tag
  │                           │
  └───────────────────────────┘
               │
               │
               ▼
  ┌───────────────────────────┐
  │                           │
  │                           │
  │                           │  - retag candidate image with
  │ release candidate promote │   official release (v1.2.3)
  │                           │
  │                           │
  │                           │
  └───────────────────────────┘
               │
               │
               ▼
  ┌───────────────────────────┐
  │                           │
  │                           │
  │    component release      │  - publish release data to
  │         publish           │    workflow-manager-api
  │                           │
  │                           │
  │                           │
  └───────────────────────────┘
               │
               │
               ▼
  ┌───────────────────────────┐
  │                           │
  │                           │ - publish signed and packaged chart
  │     component chart       │           to 'production'
  │         publish           │
  │                           │   - publish packaged chart 'dev'
  │                           │             chart repos
  │                           │
  └───────────────────────────┘
               │
               │
               ▼
  ┌───────────────────────────┐
  │                           │
  │                           │
  │                           │    - verifies signature of chart in
  │  component chart verify   │        'production' chart repo
  │                           │
  │                           │
  │                           │
  └───────────────────────────┘
```

### When Workflow-CLI is tagged
```
    Workflow CLI Release
          Pipeline

┌───────────────────────────┐
│                           │
│                           │
│                           │ - triggered by `v1.2.3` git tag
│    workflow-cli-release   │             webhook
│                           │
│                           │
│                           │
└───────────────────────────┘
            │
            │
            ▼
┌───────────────────────────┐
│                           │ - check out git tag of source
│                           │              code
│                           │ - build cross-compiled default
│  workflow-cli-build-tag   │  (linux, darwin and windows;
│                           │      amd64, 386) binaries
│                           │       - upload binaries
│                           │
└───────────────────────────┘
            │
            │
            ▼
┌───────────────────────────┐
│                           │
│                           │ - check out git tag of source
│   workflow-cli-build-     │             code
│           tag             │   - build amd64 binary with
│      darwin-amd64         │  CGO_ENABLED=1 on OSX slave
│                           │ - upload darwin amd64 binary
│                           │
└───────────────────────────┘

 Note: There are also "workflow-cli-build-stable(-darwin-amd64)"
 variants of the two downstream jobs above, but these are currently
 only triggered manually.
```

### When a Workflow Helm Chart is to be released
```

   Workflow Chart Release
          Pipeline
                                - triggered manually with supplied release
┌───────────────────────────┐                      tag
│                           │
│                           │    - update chart dependencies by gathering
│                           │    latest releases for all component charts
│   workflow-chart-stage    │
│                           │  - upload signed and packaged candidate chart
│                           │     (sans index file) to 'production' repo
│                           │
└───────────────────────────┘    - upload packaged candidate chart (with
             │                     index file) to 'staging' charts repo
             │
             ▼
┌───────────────────────────┐
│                           │   - lease GKE cluster, install Workflow chart
│                           │     (version handed down from upstream) from
│                           │                  'staging' repo
│    workflow-chart-e2e     │
│                           │           - install workflow-e2e chart
│                           │
│                           │   - archive test results and report job status
└───────────────────────────┘            to appropriate channel(s)
             │
             │
             ▼
┌───────────────────────────┐
│                           │  - triggered manually with supplied release
│                           │                     tag
│                           │
│  workflow-chart-release   │   - pull down approved, signed chart from
│                           │           'production' chart repo
│                           │
│                           │ - update index file, upload to 'production'
└───────────────────────────┘      charts repo, making it officially
             │                           fetchable/installable
             │
             ▼
┌───────────────────────────┐
│                           │
│                           │ - verifies signature of chart from 'production'
│                           │                      repo
│    helm-chart-verify      │
│                           │      - (job succeeds if command succeeds)
│                           │
│                           │
└───────────────────────────┘
```

[bats]: https://github.com/sstephenson/bats
[shellcheck]: https://github.com/koalaman/shellcheck
[gradle-prereqs]: https://docs.gradle.org/current/userguide/installation.html#sec:prerequisites
[v2.18]: https://github.com/deis/workflow/releases/tag/v2.18.0
