#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_latest_component_release.sh"
  load stub
}

@test "get-latest-component-release : default component" {
  component='controller'
  stub curl "echo '{\"component\":{\"name\":\"deis-"${component}"\"},\"version\":{\"version\":\"v1.2.3\"}}'"

  run get-latest-component-release "${component}"

  echo "${output}"
  [ "${status}" -eq 0 ]
  [ "${lines[0]}" == "Getting latest controller release via url: https://versions.deis.com/v3/versions/stable/deis-controller/latest" ]
  [ "${lines[1]}" == "v1.2.3" ]
}

@test "get-latest-component-release : component is monitor" {
  component='monitor'
  stub curl "echo '{\"component\":{\"name\":\"deis-grafana\"},\"version\":{\"version\":\"v1.2.3\"}}'"

  run get-latest-component-release "${component}"

  echo "${output}"
  [ "${status}" -eq 0 ]
  [ "${lines[0]}" == "Getting latest monitor release via url: https://versions.deis.com/v3/versions/stable/deis-grafana/latest" ]
  [ "${lines[1]}" == "v1.2.3" ]
}
