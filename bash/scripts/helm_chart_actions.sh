#!/usr/bin/env bash
set -eo pipefail

sign-helm-chart() {
  chart="${1}"
  version="${2}"

  if [ -z "${chart}" ] || [ -z "${version}" ]; then
    echo 'usage: sign-helm-chart <chart> <version>'
    return 1
  fi

  if [ -z "${SIGNING_KEY_PASSPHRASE}" ]; then
    echo 'SIGNING_KEY_PASSPHRASE must be available in the env to sign a helm chart'
    return 1
  fi

  set -x
  chart_repo="${CHART_REPO:-${chart}}"
  signing_key="${SIGNING_KEY:-Deis, Inc. (Helm chart signing key)}"
  keyring="${KEYRING:-${JENKINS_HOME}/.gnupg/secring.gpg}"

  helm repo add "${chart_repo}" https://charts.deis.com/"${chart_repo}"
  helm fetch --untar "${chart_repo}"/"${chart}" --version "${version}"
  set +x

  # HACK(vdice): create pseudo-terminal to emulate entering passphrase when prompted
  # Remove once helm supports gpg-agent/automated passphrase entry
  printf '%s\n' "${SIGNING_KEY_PASSPHRASE}" | \
    script -q -c "helm package --sign --key '${signing_key}' --keyring ${keyring} ${chart}" /dev/null &> /dev/null

  if [ ! -f "${chart}-${version}.tgz.prov" ]; then
    echo "${chart}-${version}.tgz.prov not found! Signing unsuccessful"
    return 1
  fi
}

upload-signed-chart() {
  signed_chart="${1}"
  chart_repo="${2}"

  if [ -z "${signed_chart}" ] || [ -z "${chart_repo}" ]; then
    echo 'usage: upload-signed-chart <signed_chart> <chart_repo>'
    return 1
  fi

  aws s3 cp "${signed_chart}.tgz" s3://helm-charts/"${chart_repo}"/ \
    && aws s3 cp "${signed_chart}.tgz.prov" s3://helm-charts/"${chart_repo}"/
}

download-and-init-helm() {
  export HELM_VERSION="${HELM_VERSION:-canary}"
  export HELM_OS="${HELM_OS:-linux}"
  export HELM_HOME="/home/jenkins/workspace/${JOB_NAME}/${BUILD_NUMBER}"

  wget --quiet https://storage.googleapis.com/kubernetes-helm/helm-"${HELM_VERSION}"-"${HELM_OS}"-amd64.tar.gz \
    && tar -zxvf helm-"${HELM_VERSION}"-"${HELM_OS}"-amd64.tar.gz \
    && export PATH="${HELM_OS}-amd64:${PATH}" \
    && helm init -c
}
