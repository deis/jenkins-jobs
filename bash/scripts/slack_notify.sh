#!/usr/bin/env bash

# slack-notify sends the provided message to the provided slack channel
# using the incoming-webhook url expected to be defined in the job environment
slack-notify() {
  channel="${1}"
  status="${2}"
  message="${3}"

  if [[ $# -lt 2 ]]; then
    echo "Usage: slack-notify channel status [message]"
    return 1
  fi

  title="${JOB_NAME} - ${BUILD_DISPLAY_NAME} *${status}* (<${BUILD_URL}|Open>)"

  data='
    {"channel":"'"${channel}"'",
    "attachments": [
      {
        "fallback": "'"${title}"'",
        "color": "'"$(get-status-color "${status}")"'",
        "pretext": "'"${title}"'",
        "text": "'"${message}"'",
        "mrkdwn_in": ["pretext", "text"],
        "ts": '"$(date +%s)"'
      }
    ]}
  '

  { echo "Notifying Slack with the following data:"; \
    echo "${data}"; } >&2

  curl \
    -X POST -H 'Content-type: application/json' \
    --data "${data}" \
    "${SLACK_INCOMING_WEBHOOK_URL}"
}

get-status-color() {
  status="${1}"

  local color
  case "$status" in
    'SUCCESS')
      color='good'
      ;;
    'FAILURE')
      color='danger'
      ;;
    'ABORTED'|'UNSTABLE')
      color='warning'
      ;;
    *)
      color='#ccccff'
      ;;
  esac

  echo "${color}"
}
