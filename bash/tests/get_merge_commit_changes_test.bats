#!/usr/bin/env bats

setup() {
  . "${BATS_TEST_DIRNAME}/../scripts/get_merge_commit_changes.sh"
  load stub
  stub git
}

teardown() {
  rm_stubs
}

@test "get-merge-commit-changes : default" {
  stub git "echo 'Merge: abc1234 def5678'"

  run get-merge-commit-changes 'foo1234'

  [ "${status}" -eq 0 ]
  [ "${lines[0]}" == "Returning changes from merge commit 'foo1234' using the commit range: abc1234..def5678" ]
  [ "${lines[1]}" == "Merge: abc1234 def5678" ]
}
