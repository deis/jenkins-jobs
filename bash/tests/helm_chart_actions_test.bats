#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/helm_chart_actions.sh"
  load stub

  stub wget
  stub git
  stub aws
  stub helm
  stub tar
  stub get-latest-component-release "echo 'v3.0.3'"

  PWD="${BATS_TEST_DIRNAME}/tmp"
  WORKDIR="${PWD}/charts"
  ENV_FILE_PATH="${WORKDIR}/env.file"
}

teardown() {
  rm_stubs
}

setup-publish-chart-workspace() {
  SHORT_SHA='abc1234'
  RELEASE_TAG='v1.2.3'
  EXPECTED_PRERELEASE_TAG='v1.2.4'
  TIMESTAMP="$(date -u +%Y%m%d%H%M%S)"

  chart="${1}"

  mkdir -p "${WORKDIR}/${chart}"
  echo '<Will be populated by the ci before publishing the chart>' > "${WORKDIR}/${chart}/Chart.yaml"
}

# sign-and-package-helm-chart
@test "sign-and-package-helm-chart : usage" {
  run sign-and-package-helm-chart

  [ "${status}" -eq 1 ]
  [ "${output}" == "usage: sign-and-package-helm-chart <chart>" ]
}

@test "sign-and-package-helm-chart : default, no SIGNING_KEY_PASSPHRASE" {
  run sign-and-package-helm-chart 'workflow'

  [ "${status}" -eq 1 ]
  [ "${output}" == "SIGNING_KEY_PASSPHRASE must be available in the env to sign a helm chart" ]
}

@test "sign-and-package-helm-chart : default, SIGNING_KEY_PASSPHRASE available" {
  stub script
  SIGNING_KEY_PASSPHRASE="foo"

  run sign-and-package-helm-chart 'workflow'

  [ "${output}" == "Signing packaged chart 'workflow' with key 'Deis, Inc. (Helm chart signing key)' from keyring '/.gnupg/secring.gpg'..." ]
}

@test "sign-and-package-helm-chart : non-defaults, SIGNING_KEY_PASSPHRASE available" {
  stub script
  SIGNING_KEY_PASSPHRASE="foo"
  SIGNING_KEY="Different Key"
  KEYRING="/diff/keyring/location"

  run sign-and-package-helm-chart 'workflow'

  [ "${output}" == "Signing packaged chart 'workflow' with key 'Different Key' from keyring '/diff/keyring/location'..." ]
}

# publish-helm-chart
@test "publish-helm-chart: no charts dir" {
  run publish-helm-chart

  [ "${status}" -eq 0 ]
  [ "${output}" == "No 'charts' directory found at project level; nothing to publish." ]
}

@test "publish-helm-chart: component dev" {
  chart='router'
  repo_type='dev'
  setup-publish-chart-workspace "${chart}"

  echo '"deisci" "Always" canary' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${EXPECTED_PRERELEASE_TAG}-dev-${TIMESTAMP}-sha.${SHORT_SHA}" ]
  [ "$(cat "${WORKDIR}/env.file")" == "COMPONENT_CHART_VERSION=${EXPECTED_PRERELEASE_TAG}-dev-${TIMESTAMP}-sha.${SHORT_SHA}" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == "\"deisci\" \"Always\" canary" ]
}

@test "publish-helm-chart: component pr" {
  chart='router'
  repo_type='pr'
  setup-publish-chart-workspace "${chart}"

  echo '"deisci" "Always" canary' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${EXPECTED_PRERELEASE_TAG}-sha.${SHORT_SHA}" ]
  [ "$(cat "${WORKDIR}/env.file")" == "COMPONENT_CHART_VERSION=${EXPECTED_PRERELEASE_TAG}-sha.${SHORT_SHA}" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == "\"deisci\" \"Always\" canary" ]
}

@test "publish-helm-chart: component pr, ACTUAL_COMMIT set" {
  ACTUAL_COMMIT='ghi78912345'
  chart='router'
  repo_type='pr'
  setup-publish-chart-workspace "${chart}"

  echo '"deisci" "Always" canary' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${EXPECTED_PRERELEASE_TAG}-sha.ghi7891" ]
  [ "$(cat "${WORKDIR}/env.file")" == "COMPONENT_CHART_VERSION=${EXPECTED_PRERELEASE_TAG}-sha.ghi7891" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == "\"deisci\" \"Always\" canary" ]
}

@test "publish-helm-chart: component pr" {
  chart='router'
  repo_type='pr'
  setup-publish-chart-workspace "${chart}"

  run publish-helm-chart "${chart}" "${repo_type}"

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${EXPECTED_PRERELEASE_TAG}-sha.${SHORT_SHA}" ]
}

@test "publish-helm-chart: component pr, ACTUAL_COMMIT set" {
  ACTUAL_COMMIT='ghi78912345'
  chart='router'
  repo_type='pr'
  setup-publish-chart-workspace "${chart}"

  run publish-helm-chart "${chart}" "${repo_type}"

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${EXPECTED_PRERELEASE_TAG}-sha.ghi7891" ]
}

@test "publish-helm-chart: component production, SIGN_CHART true" {
  SIGN_CHART=true
  SIGNING_KEY_PASSPHRASE='foo'
  chart='router'
  repo_type='production'
  setup-publish-chart-workspace "${chart}"

  echo '"deisci" "Always" canary' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  [ "${status}" -eq 0 ]
  [ "${output}" == "Signing packaged chart '${chart}' with key 'Deis, Inc. (Helm chart signing key)' from keyring '/.gnupg/secring.gpg'..." ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${RELEASE_TAG}" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == "\"deis\" \"IfNotPresent\" ${RELEASE_TAG}" ]
}

@test "publish-helm-chart: component production, SIGN_CHART false" {
  SIGN_CHART=false
  chart='router'
  repo_type='production'
  setup-publish-chart-workspace "${chart}"

  echo '"deisci" "Always" canary' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  [ "${status}" -eq 0 ]
  [ "${output}" == "" ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${RELEASE_TAG}" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == "\"deis\" \"IfNotPresent\" ${RELEASE_TAG}" ]
}

@test "publish-helm-chart: workflow dev" {
  chart='workflow'
  repo_type='dev'
  setup-publish-chart-workspace "${chart}"
  COMPONENT_CHART_AND_REPOS="registry:registry registry-proxy:registry-proxy database:postgres"

  echo '<registry-tag> https://charts.deis.com/registry
<registry-proxy-tag> https://charts.deis.com/registry-proxy
<database-tag> https://charts.deis.com/database' > "${WORKDIR}/${chart}/requirements.yaml"

  echo 'versions.deis.com doctor.deis.com' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  expected_requirements_yaml='">=v3.0.3-dev" https://charts.deis.com/registry-dev
">=v3.0.3-dev" https://charts.deis.com/registry-proxy-dev
">=v3.0.3-dev" https://charts.deis.com/database-dev'

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${EXPECTED_PRERELEASE_TAG}-dev-${TIMESTAMP}-sha.${SHORT_SHA}" ]
  [ "$(cat "${WORKDIR}/${chart}/requirements.yaml")" == "${expected_requirements_yaml}" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == 'versions-staging.deis.com doctor-staging.deis.com' ]
  [ "$(cat "${WORKDIR}/env.file")" == "WORKFLOW_TAG=${EXPECTED_PRERELEASE_TAG}-dev-${TIMESTAMP}-sha.${SHORT_SHA}" ]
}

@test "publish-helm-chart: workflow dev; COMPONENT_REPO and ACTUAL_COMMIT in env" {
  COMPONENT_REPO=registry-proxy
  ACTUAL_COMMIT='ghi78912345'
  chart='workflow'
  repo_type='dev'
  setup-publish-chart-workspace "${chart}"
  COMPONENT_CHART_AND_REPOS="registry:registry registry-proxy:registry-proxy database:postgres"

  echo '<registry-tag> https://charts.deis.com/registry
<registry-proxy-tag> https://charts.deis.com/registry-proxy
<database-tag> https://charts.deis.com/database' > "${WORKDIR}/${chart}/requirements.yaml"

  echo 'versions.deis.com doctor.deis.com' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  expected_requirements_yaml='">=v3.0.3-dev" https://charts.deis.com/registry-dev
">=v3.0.3-dev" https://charts.deis.com/registry-proxy-dev
">=v3.0.3-dev" https://charts.deis.com/database-dev'

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${EXPECTED_PRERELEASE_TAG}-dev-${TIMESTAMP}-sha.${SHORT_SHA}" ]
  [ "$(cat "${WORKDIR}/${chart}/requirements.yaml")" == "${expected_requirements_yaml}" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == 'versions-staging.deis.com doctor-staging.deis.com' ]
  [ "$(cat "${WORKDIR}/env.file")" == "WORKFLOW_TAG=${EXPECTED_PRERELEASE_TAG}-dev-${TIMESTAMP}-sha.${SHORT_SHA}" ]
}

@test "publish-helm-chart: workflow pr; no COMPONENT_REPO, COMPONENT_CHART_VERSION or ACTUAL_COMMIT" {
  chart='workflow'
  repo_type='pr'
  setup-publish-chart-workspace "${chart}"
  COMPONENT_CHART_AND_REPOS="registry:registry registry-proxy:registry-proxy database:postgres"

  echo '<registry-tag> https://charts.deis.com/registry
<registry-proxy-tag> https://charts.deis.com/registry-proxy
<database-tag> https://charts.deis.com/database' > "${WORKDIR}/${chart}/requirements.yaml"

  echo 'versions.deis.com doctor.deis.com' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  expected_requirements_yaml='">=v3.0.3-dev" https://charts.deis.com/registry-dev
">=v3.0.3-dev" https://charts.deis.com/registry-proxy-dev
">=v3.0.3-dev" https://charts.deis.com/database-dev'

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${EXPECTED_PRERELEASE_TAG}-sha.${SHORT_SHA}" ]
  [ "$(cat "${WORKDIR}/${chart}/requirements.yaml")" == "${expected_requirements_yaml}" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == 'versions-staging.deis.com doctor-staging.deis.com' ]
  [ "$(cat "${WORKDIR}/env.file")" == "WORKFLOW_TAG=${EXPECTED_PRERELEASE_TAG}-sha.${SHORT_SHA}" ]
}

@test "publish-helm-chart: workflow pr; ACTUAL_COMMIT in env" {
  ACTUAL_COMMIT='ghi78912345'
  chart='workflow'
  repo_type='pr'
  setup-publish-chart-workspace "${chart}"

  echo 'versions.deis.com doctor.deis.com' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${EXPECTED_PRERELEASE_TAG}-sha.ghi7891" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == 'versions-staging.deis.com doctor-staging.deis.com' ]
  [ "$(cat "${WORKDIR}/env.file")" == "WORKFLOW_TAG=${EXPECTED_PRERELEASE_TAG}-sha.ghi7891" ]
}

@test "publish-helm-chart: workflow pr; COMPONENT_REPO, COMPONENT_CHART_VERSION and ACTUAL_COMMIT in env" {
  COMPONENT_REPO=registry-proxy
  COMPONENT_CHART_VERSION="v3.0.4-sha.jkl5678"
  ACTUAL_COMMIT='ghi78912345'
  chart='workflow'
  repo_type='pr'
  setup-publish-chart-workspace "${chart}"
  COMPONENT_CHART_AND_REPOS="registry:registry registry-proxy:registry-proxy database:postgres"

  echo '<registry-tag> https://charts.deis.com/registry
<registry-proxy-tag> https://charts.deis.com/registry-proxy
<database-tag> https://charts.deis.com/database' > "${WORKDIR}/${chart}/requirements.yaml"

  echo 'versions.deis.com doctor.deis.com' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  expected_requirements_yaml='">=v3.0.3-dev" https://charts.deis.com/registry-dev
"'"${COMPONENT_CHART_VERSION}"'" https://charts.deis.com/registry-proxy-pr
">=v3.0.3-dev" https://charts.deis.com/database-dev'

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${EXPECTED_PRERELEASE_TAG}-sha.${SHORT_SHA}" ]
  [ "$(cat "${WORKDIR}/${chart}/requirements.yaml")" == "${expected_requirements_yaml}" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == 'versions-staging.deis.com doctor-staging.deis.com' ]
  [ "$(cat "${WORKDIR}/env.file")" == "WORKFLOW_TAG=${EXPECTED_PRERELEASE_TAG}-sha.${SHORT_SHA}" ]
}

@test "publish-helm-chart: workflow staging" {
  SIGNING_KEY_PASSPHRASE='foo'
  chart='workflow'
  repo_type='staging'
  setup-publish-chart-workspace "${chart}"
  COMPONENT_CHART_AND_REPOS="registry:registry registry-proxy:registry-proxy database:postgres"

  echo '<registry-tag> https://charts.deis.com/registry
<registry-proxy-tag> https://charts.deis.com/registry-proxy
<database-tag> https://charts.deis.com/database' > "${WORKDIR}/${chart}/requirements.yaml"

  echo 'versions.deis.com doctor.deis.com' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  expected_requirements_yaml='"v3.0.3" https://charts.deis.com/registry
"v3.0.3" https://charts.deis.com/registry-proxy
"v3.0.3" https://charts.deis.com/database'

  expected_output=''"${expected_requirements_yaml}"'
Signing packaged chart '"'workflow'"' with key '"'Deis, Inc. (Helm chart signing key)'"' from keyring '"'/.gnupg/secring.gpg'"'...'

  [ "${status}" -eq 0 ]
  [ "${output}" == "${expected_output}" ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${RELEASE_TAG}" ]
  [ "$(cat "${WORKDIR}/${chart}/requirements.yaml")" == "${expected_requirements_yaml}" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == 'versions-staging.deis.com doctor-staging.deis.com' ]
  [ "$(cat "${WORKDIR}/env.file")" == "WORKFLOW_TAG=${RELEASE_TAG}" ]
}

@test "publish-helm-chart: workflow production" {
  chart='workflow'
  repo_type='production'
  setup-publish-chart-workspace "${chart}"
  COMPONENT_CHART_AND_REPOS="registry:registry registry-proxy:registry-proxy database:postgres"

  echo '<registry-tag> https://charts.deis.com/registry
<registry-proxy-tag> https://charts.deis.com/registry-proxy
<database-tag> https://charts.deis.com/database' > "${WORKDIR}/${chart}/requirements.yaml"

  echo 'versions.deis.com doctor.deis.com' > "${WORKDIR}/${chart}/values.yaml"

  run publish-helm-chart "${chart}" "${repo_type}"

  expected_requirements_yaml='"v3.0.3" https://charts.deis.com/registry
"v3.0.3" https://charts.deis.com/registry-proxy
"v3.0.3" https://charts.deis.com/database'

  [ "${status}" -eq 0 ]
  [ "$(cat "${WORKDIR}/${chart}/Chart.yaml")" == "${RELEASE_TAG}" ]
  [ "$(cat "${WORKDIR}/${chart}/requirements.yaml")" == "${expected_requirements_yaml}" ]
  [ "$(cat "${WORKDIR}/${chart}/values.yaml")" == 'versions.deis.com doctor.deis.com' ]
  [ "$(cat "${WORKDIR}/env.file")" == "WORKFLOW_TAG=${RELEASE_TAG}" ]
}
