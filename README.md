# Deis Workflow Jenkins Jobs

Deis (pronounced DAY-iss) Workflow is an open source Platform as a Service (PaaS) that adds a developer-friendly layer to any [Kubernetes](http://kubernetes.io) cluster, making it easy to deploy and manage applications on your own servers.

For more information about the Deis Workflow, please visit the main project page at https://github.com/deis/workflow.

# About

This repository serves as a central location for [Deis Workflow Jenkins jobs](https://ci.deis.io) represented in [Jenkins Job DSL](https://github.com/jenkinsci/job-dsl-plugin).

## Resources

The definitive wiki resource describing all available Jenkins Job DSL API items can be found in the [Jenkins Job DSL Plugin API](https://jenkinsci.github.io/job-dsl-plugin/).

Until we add support for [testing DSL changes](https://github.com/deis/jenkins-jobs/issues/39) while developing, the [Jenkins Job DSL Playground](http://job-dsl.herokuapp.com/) can be used for verifying that the DSL parses correctly.  (If it does, an `xml` file will be generated.  For comparison, you can view an existing job's `xml` equivalent by navigating to `https://ci.deis.io/job/<job-name>/config.xml`)

## License

Copyright 2013, 2014, 2015, 2016 Engine Yard, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[issues]: https://github.com/deis/jenkins-jobs/issues
[prs]: https://github.com/deis/jenkins-jobs/pulls
