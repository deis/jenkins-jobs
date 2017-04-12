#!/usr/bin/env bash

set -eo pipefail

cleanup() {
  buckets=$*
  echo "Buckets to be removed: ${buckets}"
  if [ "${STORAGE_TYPE}" == "s3" ]; then
    echo 'Cleaning up buckets in aws/s3 created during tests...'
    for bucket in ${buckets}; do
      aws s3 rm s3://"${bucket}" --recursive
      aws s3 rb s3://"${bucket}" --force
    done
  elif [ "${STORAGE_TYPE}" == "gcs" ]; then
    echo 'Cleaning up buckets in gcs created during tests...'
    docker run --rm \
      -e GCS_KEY_JSON="${GCS_KEY_JSON}" \
      -e BUILD_NUMBER="${BUILD_NUMBER}" \
      google/cloud-sdk \
      sh -c "echo \${GCS_KEY_JSON} | base64 -d - > /tmp/key.json \
&& gcloud auth activate-service-account -q --key-file /tmp/key.json \
&& for bucket in ${buckets}; do gsutil -m rm -r gs://\${bucket}; done"
  fi
  echo 'Buckets removed.'
}
