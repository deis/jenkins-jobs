FROM gcr.io/google_containers/ubuntu-slim:0.3

ENV BATS_VERSION 0.4.0

RUN addgroup --gid 999 batsman
RUN adduser --system \
	--shell /bin/bash \
	--disabled-password \
	--gid 999 \
	--uid 999 \
	batsman

RUN apt-get update -q \
	&& apt-get install -y -q --no-install-recommends bash make curl ca-certificates \
	&& curl -o "/tmp/v${BATS_VERSION}.tar.gz" -L \
		"https://github.com/sstephenson/bats/archive/v${BATS_VERSION}.tar.gz" \
	&& tar -x -z -f "/tmp/v${BATS_VERSION}.tar.gz" -C /tmp/ \
	&& bash "/tmp/bats-${BATS_VERSION}/install.sh" /usr/local \
	&& rm -rf /tmp/*

USER batsman

ENTRYPOINT ["/usr/local/bin/bats"]
