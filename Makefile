# -e SC2154 to exclude the following shellcheck warning:
# '^-- SC2154: ghprbPullLongDescription is referenced but not assigned.'
SHELLCHECK_CMD := shellcheck -e SC2154 bash/scripts/*
BATS_CMD := bats --tap bash/tests
TEST_ENV_PREFIX := docker run --rm -v ${CURDIR}:/workdir -w /workdir quay.io/deis/shell-dev

test:
	${SHELLCHECK_CMD}
	${BATS_CMD}

docker-test:
	${TEST_ENV_PREFIX} ${SHELLCHECK_CMD}
	${TEST_ENV_PREFIX} ${BATS_CMD}

.PHONY: test docker-test
