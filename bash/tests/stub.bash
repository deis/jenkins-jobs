export TMP_STUB_PATH=tmp
export PATH="${BATS_TEST_DIRNAME}/${TMP_STUB_PATH}:${PATH}"

if [ ! -d ${BATS_TEST_DIRNAME}/${TMP_STUB_PATH} ]; then
  mkdir -p ${BATS_TEST_DIRNAME}/${TMP_STUB_PATH}
fi

stub() {
  main="${2}"
  exit_code="${3}"

  stub_template="\
    #!/bin/bash
    set -eo pipefail

    ${main}
    exit ${exit_code}
"

  echo "${stub_template}" > ${BATS_TEST_DIRNAME}/${TMP_STUB_PATH}/${1}
  chmod +x ${BATS_TEST_DIRNAME}/${TMP_STUB_PATH}/${1}
}
rm_stubs() {
  rm -rf ${BATS_TEST_DIRNAME}/${TMP_STUB_PATH}
}
