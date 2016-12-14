#!/usr/bin/env bash

set -eo pipefail

export DEIS_CHARTS_BASE_URL="https://charts.deis.com"
export DEIS_CHARTS_BUCKET_BASE_URL="s3://helm-charts"

# sign-and-package-helm-chart signs and packages the helm chart provided by chart,
# expecting a signing key passphrase in SIGNING_KEY_PASSPHRASE.
sign-and-package-helm-chart() {
  chart="${1}"

  if [ -z "${chart}" ]; then
    echo 'usage: sign-and-package-helm-chart <chart>'
    return 1
  fi

  if [ -z "${SIGNING_KEY_PASSPHRASE}" ]; then
    echo 'SIGNING_KEY_PASSPHRASE must be available in the env to sign a helm chart'
    return 1
  fi

  signing_key="${SIGNING_KEY:-Deis, Inc. (Helm chart signing key)}"
  keyring="${KEYRING:-${JENKINS_HOME}/.gnupg/secring.gpg}"

  echo "Signing packaged chart '${chart}' with key '${signing_key}' from keyring '${keyring}'..." >&2

  # HACK(vdice): create pseudo-terminal to emulate entering passphrase when prompted
  # Remove once helm supports gpg-agent/automated passphrase entry
  printf '%s\n' "${SIGNING_KEY_PASSPHRASE}" | \
    script -q -c "helm package --sign --key '${signing_key}' --keyring ${keyring} ${chart}" /dev/null &> /dev/null
}

# download-and-init-helm downloads helm based on HELM_VERSION and HELM_OS and
# runs 'helm init -c' using HELM_HOME
download-and-init-helm() {
  export HELM_VERSION="${HELM_VERSION:-canary}"
  export HELM_OS="${HELM_OS:-linux}"
  export HELM_HOME="/home/jenkins/workspace/${JOB_NAME}/${BUILD_NUMBER}"

  wget --quiet https://storage.googleapis.com/kubernetes-helm/helm-"${HELM_VERSION}"-"${HELM_OS}"-amd64.tar.gz \
    && tar -zxvf helm-"${HELM_VERSION}"-"${HELM_OS}"-amd64.tar.gz \
    && export PATH="${HELM_OS}-amd64:${PATH}" \
    && helm init -c
}

# publish-helm-chart publishes the given chart to the chart repo determined
# by the given repo_type.  Will also attempt to sign chart if SIGN_CHART is true OR
# repo_type is 'staging'
publish-helm-chart() {
  local chart="${1}"
  local repo_type="${2}"

  # give ACTUAL_COMMIT precedence for use in chart versioning, assuming COMPONENT_REPO is empty/null
  # otherwise, ACTUAL_COMMIT is tied to the COMPONENT_REPO for use in assembling the workflow chart below
  # shellcheck disable=SC2153
  if [ -n "${ACTUAL_COMMIT}" ] && [ -z "${COMPONENT_REPO}" ]; then
    SHORT_SHA="${ACTUAL_COMMIT:0:7}"
  fi

  # if repo_type not 'staging', check out RELEASE_TAG tag (if empty, just stays on master commit)
  if [ "${repo_type}" != 'staging' ]; then
    git checkout -q "${RELEASE_TAG}"
  fi

  short_sha="${SHORT_SHA:-$(git rev-parse --short HEAD)}"
  git_tag="${RELEASE_TAG:-$(git describe --abbrev=0 --tags)}"
  timestamp="${TIMESTAMP:-$(date -u +%Y%m%d%H%M%S)}"
  chart_repo="$(echo "${chart}-${repo_type}" | sed -e 's/-production//g')"

  if [ -d "${PWD}"/charts ]; then
    cd "${PWD}"/charts
    download-and-init-helm

    chart_version="${git_tag}"
    # if dev/pr chart, will use incremented patch version (v1.2.3 -> v1.2.4) and add prerelease build info
    incremented_patch_version="$(( ${chart_version: -1} +1))"
    if [ "${chart_repo}" == "${chart}-dev" ]; then
      chart_version="${chart_version%?}${incremented_patch_version}-dev-${timestamp}-sha.${short_sha}"
    elif [ "${chart_repo}" == "${chart}-pr" ]; then
      chart_version="${chart_version%?}${incremented_patch_version}-${timestamp}-sha.${short_sha}"
    fi

    update-chart "${chart}" "${chart_version}" "${chart_repo}"

    if [ "${SIGN_CHART}" == true ]; then
      sign-and-package-helm-chart "${chart}"
      aws s3 cp "${chart}-${chart_version}".tgz.prov "${DEIS_CHARTS_BUCKET_BASE_URL}/${chart}"/
    else
      helm package "${chart}"
    fi

    # download index file from aws s3 bucket
    aws s3 cp "${DEIS_CHARTS_BUCKET_BASE_URL}/${chart_repo}/index.yaml" .

    # update index file
    helm repo index . --url "${DEIS_CHARTS_BASE_URL}/${chart_repo}" --merge ./index.yaml

    # push packaged chart and updated index file to aws s3 bucket
    aws s3 cp "${chart}-${chart_version}".tgz "${DEIS_CHARTS_BUCKET_BASE_URL}/${chart_repo}"/ \
      && aws s3 cp --cache-control max_age=0 index.yaml "${DEIS_CHARTS_BUCKET_BASE_URL}/${chart_repo}"/index.yaml \
      && aws s3 cp "${chart}"/values.yaml "${DEIS_CHARTS_BUCKET_BASE_URL}/${chart_repo}/values-${chart_version}".yaml
  else
    echo "No 'charts' directory found at project level; nothing to publish."
  fi
}

# update-chart updates a given chart, using the provided chart, chart_version
# and chart_repo values.  If the chart is 'workflow', a space-delimited list of
# component charts is expected to be present in a COMPONENT_CHART_AND_REPOS env var
update-chart() {
  local chart="${1}"
  local chart_version="${2}"
  local chart_repo="${3}"

  # update the chart version
  perl -i -0pe "s/<Will be populated by the ci before publishing the chart>/${chart_version}/g" "${chart}"/Chart.yaml

  if [ "${chart}" != 'workflow' ]; then
    ## make component chart updates
    if [ "${chart_repo}" == "${chart}" ]; then
      ## chart repo is production repo; update values appropriately
      # update all org values to "deis"
      perl -i -0pe 's/"deisci"/"deis"/g' "${chart}"/values.yaml
      # update the image pull policy to "IfNotPresent"
      perl -i -0pe 's/"Always"/"IfNotPresent"/g' "${chart}"/values.yaml
      # update the dockerTag value to chart_version
      perl -i -0pe "s/canary/${chart_version}/g" "${chart}"/values.yaml
    fi
    # send chart version on for use in downstream jobs
    echo "COMPONENT_CHART_VERSION=${chart_version}" >> "${ENV_FILE_PATH:-/dev/null}"
  else
    ## make workflow chart updates
    # update requirements.yaml with correct chart version and chart repo for each component
    for component in ${COMPONENT_CHART_AND_REPOS}; do
      IFS=':' read -r -a chart_and_repo <<< "${component}"
      component_chart="${chart_and_repo[0]}"
      component_repo="${chart_and_repo[1]}"
      latest_tag="$(get-latest-component-release "${component_repo}")"

      component_chart_version="${latest_tag}"
      component_chart_repo="${component_chart}"
      # if COMPONENT_REPO matches this component repo and COMPONENT_CHART_VERSION is non-empty/non-null,
      # this signifies we need to set component chart version to correlate with PR artifact
      # shellcheck disable=SC2153
      if [ "${COMPONENT_REPO}" == "${component_repo}" ] && [ -n "${COMPONENT_CHART_VERSION}" ] && [ "${chart_repo}" == "${chart}-pr" ]; then
        component_chart_version="${COMPONENT_CHART_VERSION}"
        component_chart_repo="${component_chart}-pr"
      elif [ "${chart_version}" != "${git_tag}" ]; then
        # workflow chart version has build data; is -dev variant. assign component version/repo accordingly
        component_chart_version=">=${latest_tag}-dev"
        component_chart_repo="${component_chart}-dev"
      fi

      perl -i -0pe 's/<'"${component_chart}"'-tag>/"'"${component_chart_version}"'"/g' "${chart}"/requirements.yaml
      perl -i -0pe 's='"${DEIS_CHARTS_BASE_URL}/${component_chart}\n"'='"${DEIS_CHARTS_BASE_URL}/${component_chart_repo}\n"'=g' "${chart}"/requirements.yaml
      helm repo add "${component_chart_repo}" "${DEIS_CHARTS_BASE_URL}/${component_chart_repo}"

      # DEBUG
      helm search "${component_chart_repo}"/"${component_chart}" -l
    done

    # TEMP FIX: remove when registry-proxy no longer under deis (https://github.com/deis/workflow/issues/644 closed)
    perl -i -0pe 's/<registry-proxy-tag>/"v1.1.1"/g' "${chart}"/requirements.yaml
    helm repo add registry-proxy "${DEIS_CHARTS_BASE_URL}/registry-proxy"

    # DEBUG
    helm repo list

    # display resulting requirements.yaml to verify component chart versions
    cat "${chart}"/requirements.yaml

    # fetch all dependent charts based on above
    helm dependency update "${chart}"

    if [ "${chart_repo}" == "${chart}-staging" ]; then
      # 'stage' signed chart on production sans index.file (so chart may not be used
      # but is ready to copy to production repo with index.file if approved)
      sign-and-package-helm-chart "${chart}"

      aws s3 cp "${chart}-${chart_version}".tgz "${DEIS_CHARTS_BUCKET_BASE_URL}/${chart}"/ \
        && aws s3 cp "${chart}-${chart_version}".tgz.prov "${DEIS_CHARTS_BUCKET_BASE_URL}/${chart}"/ \
        && aws s3 cp "${chart}"/values.yaml "${DEIS_CHARTS_BUCKET_BASE_URL}/${chart}/values-${chart_version}".yaml
    fi

    if [ "${chart_repo}" != "${chart}" ]; then
      # modify workflow-manager/doctor urls in values.yaml to point to staging
      perl -i -0pe "s/versions.deis/versions-staging.deis/g" "${chart}"/values.yaml
      perl -i -0pe "s/doctor.deis/doctor-staging.deis/g" "${chart}"/values.yaml
    fi

    # set WORKFLOW_TAG for downstream e2e job to read from
    echo "WORKFLOW_TAG=${chart_version}" >> "${ENV_FILE_PATH:-/dev/null}"
  fi
}
