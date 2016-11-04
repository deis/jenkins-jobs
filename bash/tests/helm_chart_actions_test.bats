#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/helm_chart_actions.sh"
  load stub
  stub helm

  CHART='workflow'
  VERSION='v1.2.3'
}

teardown() {
  rm_stubs
}

@test "sign-helm-chart : CHART and VERSION missing" {
  run sign-helm-chart

  [ "${status}" -eq 1 ]
  [ "${output}" == "usage: sign-helm-chart <chart> <version>" ]
}

@test "sign-helm-chart : SIGNING_KEY_PASSPHRASE not in env" {
  run sign-helm-chart "${CHART}" "${VERSION}"

  [ "${status}" -eq 1 ]
  [ "${output}" == "SIGNING_KEY_PASSPHRASE must be available in the env to sign a helm chart" ]
}

@test "sign-helm-chart : defaults" {
  SIGNING_KEY_PASSPHRASE="foo"

  run sign-helm-chart "${CHART}" "${VERSION}"

  [ "${status}" -eq 1 ]
  [ "${lines[0]}" == "++ chart_repo=workflow" ]
  [ "${lines[1]}" == "++ signing_key='Deis, Inc. (Helm chart signing key)'" ]
  [ "${lines[2]}" == "++ keyring=/.gnupg/secring.gpg" ]
  [ "${lines[3]}" == "++ helm repo add workflow https://charts.deis.com/workflow" ]
  [ "${lines[4]}" == "++ helm fetch --untar workflow/workflow --version v1.2.3" ]
  [ "${lines[5]}" == "++ set +x" ]
  [ "${lines[6]}" == "${CHART}-${VERSION}.tgz.prov not found! Signing unsuccessful" ]
}

@test "sign-helm-chart : override defaults via env" {
  SIGNING_KEY_PASSPHRASE="foo"
  CHART_REPO="workflow-dev"
  SIGNING_KEY="Different Key"
  KEYRING="/diff/keyring/location"

  run sign-helm-chart "${CHART}" "${VERSION}"

  [ "${status}" -eq 1 ]
  [ "${lines[0]}" == "++ chart_repo=workflow-dev" ]
  [ "${lines[1]}" == "++ signing_key='Different Key'" ]
  [ "${lines[2]}" == "++ keyring=/diff/keyring/location" ]
  [ "${lines[3]}" == "++ helm repo add workflow-dev https://charts.deis.com/workflow-dev" ]
  [ "${lines[4]}" == "++ helm fetch --untar workflow-dev/workflow --version v1.2.3" ]
  [ "${lines[5]}" == "++ set +x" ]
  [ "${lines[6]}" == "${CHART}-${VERSION}.tgz.prov not found! Signing unsuccessful" ]
}
