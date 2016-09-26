#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/slack_notify.sh"
  load stub
  stub curl

  JOB_NAME="test-job"
  BUILD_DISPLAY_NAME="#42"
  BUILD_URL="test-job.url"
}

teardown() {
  rm_stubs
}

strip-ws() {
  echo -e "${1}" | tr -d '[[:space:]]'
}

@test "get-status-color: SUCCESS" {
  run get-status-color "SUCCESS"

  [ "${status}" -eq 0 ]
  [ "${output}" == "good" ]
}

@test "get-status-color: FAILURE" {
  run get-status-color "FAILURE"

  [ "${status}" -eq 0 ]
  [ "${output}" == "danger" ]
}

@test "get-status-color: ABORTED" {
  run get-status-color "ABORTED"

  [ "${status}" -eq 0 ]
  [ "${output}" == "warning" ]
}

@test "get-status-color: UNSTABLE" {
  run get-status-color "UNSTABLE"

  [ "${status}" -eq 0 ]
  [ "${output}" == "warning" ]
}

@test "get-status-color: no status supplied" {
  run get-status-color

  [ "${status}" -eq 0 ]
  [ "${output}" == "#ccccff" ]
}

@test "slack-notify: default" {
  channel="test-channel"
  buildStatus="SUCCESS"
  message='
    line 1
    line 2
  '

  expected_color='good'
  expected_title="${JOB_NAME} - ${BUILD_DISPLAY_NAME} *${buildStatus}* (<${BUILD_URL}|Open>)"
  expected_data='
    {"channel":"'"${channel}"'",
    "attachments": [
      {
        "fallback": "'"${expected_title}"'",
        "color": "'"${expected_color}"'",
        "pretext": "'"${expected_title}"'",
        "text": "'"${message}"'",
        "mrkdwn_in": ["pretext", "text"],
        "ts": '"$(date +%s)"'
      }
    ]}
  '
  expected_output='
    Notifying Slack with the following data:
    '"${expected_data}"'
  '

  run slack-notify "${channel}" "${buildStatus}" "${message}"

  [ "${status}" -eq 0 ]
  [ "$(strip-ws "${output}")" == "$(strip-ws "${expected_output}")" ]
}

@test "slack-notify: usage" {
  run slack-notify

  [ "${status}" -eq 1 ]
  [ "${output}" == "Usage: slack-notify channel status [message]" ]
}
