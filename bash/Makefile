TEST_CMD := --tap tests

test:
	bats ${TEST_CMD}

docker-build:
	docker build -t bats .

docker-test: docker-build
	docker run -v ${CURDIR}:/bash -w /bash bats ${TEST_CMD}

.PHONY: test docker-test docker-build
