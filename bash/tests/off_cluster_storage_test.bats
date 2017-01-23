#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/off_cluster_storage.sh"
  load stub
  stub aws
  stub docker

  BUCKETS="builder database registry"
}

teardown() {
  rm_stubs
}

# cleanup tests

@test "cleanup : storage_type empty" {
  run cleanup "${BUCKETS}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" == "Buckets to be removed: ${BUCKETS}" ]
  [ "${lines[1]}" == "Buckets removed." ]
}

@test "cleanup : storage_type s3" {
  STORAGE_TYPE="s3"
  run cleanup "${BUCKETS}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" == "Buckets to be removed: ${BUCKETS}" ]
  [ "${lines[1]}" == "Cleaning up buckets in aws/s3 created during tests..." ]
  [ "${lines[2]}" == "Buckets removed." ]
}

@test "cleanup : storage_type gcs" {
  STORAGE_TYPE="gcs"
  run cleanup "${BUCKETS}"

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" == "Buckets to be removed: ${BUCKETS}" ]
  [ "${lines[1]}" == "Cleaning up buckets in gcs created during tests..." ]
  [ "${lines[2]}" == "Buckets removed." ]
}
