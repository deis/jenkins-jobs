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

### When a Component PR is Created
```
Build────────────────────┐          - check out source code from PR commit
│                        │          - build test Docker image
│      "logger-pr"       │          - report build job status to GitHub PR
│                        │          - if success, initiate E2E job
└──────────┬─────────────┘
           │
           ▼
E2E──────────────────────┐
│                        │          - set GitHub PR status to 'Pending'
│ "workflow-test-pr"     │          - run end-to-end tests against test image supplied
│  COMPONENT_REPO=logger │          - report e2e result back to GitHub PR
│                        │
└────────────────────────┘
```

### When a Component PR is Merged to Master
```
Build────────────────────┐
│                        │          - check out source code
│      "logger"          │          - build test Docker image
│                        │          - on success, initiate E2E job
└──────────┬─────────────┘
           │
           ▼
E2E──────────────────────┐
│                        │
│  "workflow-test"       │          - run end-to-end tests against test image supplied
│  COMPONENT_REPO=logger │          - on success, initiate promote job
│                        │
└──────────┬─────────────┘
           │
           ▼
Promote──────────────────┐
│                        │          - pull image from test repo
│ "component-promote"    │          - push/promote image to production repo
│  COMPONENT_NAME=logger │          - use the git SHA tag for both
│                        │
└────────────────────────┘
```

### When a Component is Tagged
```
Locate Candidate──────────────┐
│                             │     - triggered by `v1.2.3` git tag push webhook
│    "logger-release"         │     - locate release candidate associated to git tag (promoted above)
│                             │     - send to downstream E2E job and wait for final status
└─────────────┬───────────────┘
              │
              ▼
Promote───────────────────────┐
│                             │
│ "release-candidate-promote" │     - retag candidate image with official RELEASE_TAG (v1.2.3)
│    COMPONENT_NAME=logger    │     - on success, initiate publish job
│    RELEASE_TAG=v1.2.3       │
│                             │
└─────────────┬───────────────┘
              │
              ▼
Chart Publish─────────────────┐
│                             │
│    "logger-chart-publish"   │     - if component has correlating helm chart...
│     RELEASE_TAG=v1.2.3      │     - reference official image created above
│                             │     - publish `v1.2.3`-versioned chart to charts.deis.com
│                             │
└─────────────┬───────────────┘
              │
              ▼
Publish───────────────────────┐
│                             │
│ "component-release-publish" │
│    COMPONENT=logger         │     - publish release data to workflow-manager-api
│    RELEASE=v1.2.3           │
│                             │
└─────────────────────────────┘
```

### When Workflow-CLI is tagged
```
Trigger─────────────────────────────────┐
│                                       │   - triggered by `v1.2.3` git tag push webhook
│       "workflow-cli-release"          │   - pass this to downstream job(s)
│                                       │
└──────────┬────────────────────────────┘
           │
           ▼
Build and Release - defaults────────────┐
│                                       │   - check out TAG of source code
│      "workflow-cli-build-tag"         │   - build cross-compiled default (linux, darwin and windows; amd64, 386) binaries
│       TAG=v1.2.4                      │   - upload binaries
│                                       │
└──────────┬────────────────────────────┘
           │
           ▼
Build and Release - darwin amd64────────┐
│                                       │   - check out TAG of source code
│ "workflow-cli-build-tag-darwin-amd64" |   - build darwin amd64 binary with CGO_ENABLED=1 on OSX slave
│  TAG=v1.2.4                           │   - upload darwin amd64 binary
│                                       │
└───────────────────────────────────────┘

Note: There are also "workflow-cli-build-stable(-darwin-amd64)" variants of the two downstream jobs above, but these
are currently only triggered manually.
```

## License

Copyright 2013, 2014, 2015, 2016 Engine Yard, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[bats]: https://github.com/sstephenson/bats
[shellcheck]: https://github.com/koalaman/shellcheck
[gradle-prereqs]: https://docs.gradle.org/current/userguide/installation.html#sec:prerequisites
