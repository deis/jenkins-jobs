# Deis Workflow Jenkins Jobs

Deis (pronounced DAY-iss) Workflow is an open source Platform as a Service (PaaS) that adds a developer-friendly layer to any [Kubernetes](http://kubernetes.io) cluster, making it easy to deploy and manage applications on your own servers.

For more information about the Deis Workflow, please visit the main project page at https://github.com/deis/workflow.

# About

This repository serves as a central location for [Deis Workflow Jenkins jobs](https://ci.deis.io) represented in [Jenkins Job DSL](https://github.com/jenkinsci/job-dsl-plugin).

## Resources

The definitive wiki resource describing all available Jenkins Job DSL API items can be found in the [Jenkins Job DSL Plugin API](https://jenkinsci.github.io/job-dsl-plugin/).

Until we add support for [testing DSL changes](https://github.com/deis/jenkins-jobs/issues/39) while developing, the [Jenkins Job DSL Playground](http://job-dsl.herokuapp.com/) can be used for verifying that the DSL parses correctly.  (If it does, an `xml` file will be generated.  For comparison, you can view an existing job's `xml` equivalent by navigating to `https://ci.deis.io/job/<job-name>/config.xml`)


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
E2E───────────────────────────┐
│                             │
│   "release-candidate-e2e"   │     - run end-to-end tests against release candidate image
│   COMPONENT_NAME=logger     │     - on success, initiate promote job
│                             │
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
Publish───────────────────────┐
│                             │
│ "component-release-publish" │
│    COMPONENT=logger         │     - publish release data to workflow-manager-api
│    RELEASE=v1.2.3           │
│                             │
└─────────────────────────────┘
```

## License

Copyright 2013, 2014, 2015, 2016 Engine Yard, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[issues]: https://github.com/deis/jenkins-jobs/issues
[prs]: https://github.com/deis/jenkins-jobs/pulls
