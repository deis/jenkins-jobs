export TMP_STUB_PATH=tmp
export PATH="${BATS_TEST_DIRNAME}/${TMP_STUB_PATH}:${PATH}"

stub() {
  if [ ! -d ${BATS_TEST_DIRNAME}/${TMP_STUB_PATH} ]; then
    mkdir -p ${BATS_TEST_DIRNAME}/${TMP_STUB_PATH}
  fi

  echo "${2}" > ${BATS_TEST_DIRNAME}/${TMP_STUB_PATH}/${1}
  chmod +x ${BATS_TEST_DIRNAME}/${TMP_STUB_PATH}/${1}
}
rm_stubs() {
  rm -rf ${BATS_TEST_DIRNAME}/${TMP_STUB_PATH}
}
