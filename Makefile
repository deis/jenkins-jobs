SHELLCHECK_CMD := shellcheck bash/scripts/*
BATS_CMD := bats --tap bash/tests
TEST_ENV_PREFIX := docker run --rm -v ${CURDIR}:/workdir -w /workdir quay.io/deis/shell-dev

test:
	${SHELLCHECK_CMD}
	${BATS_CMD}

docker-test:
	${TEST_ENV_PREFIX} ${SHELLCHECK_CMD}
	${TEST_ENV_PREFIX} ${BATS_CMD}

.PHONY: test docker-test
