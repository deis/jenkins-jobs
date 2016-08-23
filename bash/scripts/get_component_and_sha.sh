#!/usr/bin/env bash

set -eo pipefail

# Breaks up a <COMPONENT>_SHA env var into component name and sha
get-component-and-sha() {
  # populate env_var_array with all <COMPONENT>_SHA env vars
  IFS=' ' read -r -a env_var_array <<< "$(compgen -A variable | grep _SHA)"

  # only one env var should be non-empty
  for env_var in "${env_var_array[@]}"; do
    if [ -n "${!env_var}" ]; then
      component_name="$(echo "${env_var%_SHA}" | perl -ne 'print lc' | sed 's/_/-/g')"
      component_sha="${!env_var}"

      if [ "${component_name}" == 'workflow-cli' ]; then
        echo SKIP_COMPONENT_PROMOTE=true
      fi

      { echo COMPONENT_NAME="${component_name}"; \
        echo COMPONENT_SHA="${component_sha}"; }
    fi
  done
}
