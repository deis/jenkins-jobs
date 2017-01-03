SHELLCHECK_CMD := shellcheck bash/scripts/*
BATS_CMD := bats --tap bash/tests

TEST_ENV_PREFIX := docker run --rm -v ${CURDIR}:/workdir -w /workdir quay.io/deis/shell-dev:latest
GRADLE_TEST_CMD := docker run --name gradle-test -v ${CURDIR}:/workdir -w /workdir frekele/gradle:3.2.1-jdk8 ./gradlew test

test-style:
	${SHELLCHECK_CMD}

test-scripts:
	${BATS_CMD}

test-dsl:
	./gradlew test

test: test-style test-scripts test-dsl

docker-test-style:
	${TEST_ENV_PREFIX} ${SHELLCHECK_CMD}

docker-test-scripts:
	${TEST_ENV_PREFIX} ${BATS_CMD}

docker-test-dsl:
	-docker rm gradle-test
	${GRADLE_TEST_CMD}

docker-test-dsl-quick:
	(docker start gradle-test \
		&& docker logs -f --tail 0 gradle-test) || ${GRADLE_TEST_CMD}

docker-test: docker-test-style docker-test-scripts docker-test-dsl

open-test-results:
	open build/reports/tests/test/index.html

groovy-console:
	./gradlew console

.PHONY: test-style test-scripts test-dsl test \
	docker-test-style docker-test-scripts docker-test-dsl docker-test-dsl-quick \
	docker-test open-test-results groovy-console
