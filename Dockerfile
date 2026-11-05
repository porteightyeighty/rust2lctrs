# The demo server, with Cora and Z3 so /v1/analyse can return termination verdicts.

FROM docker.io/library/eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN ./mvnw -B -q -DskipTests package

# Cora, pinned to a commit on its development branch: master predates the unbounded Int sort
# (hezzel/cora#10), which the 64- and 128-bit width bounds need.
FROM docker.io/library/eclipse-temurin:21-jdk AS cora
ARG CORA_COMMIT=fef120b861e7c82e5aa9bbf05087f52dd0ccb535
WORKDIR /cora
RUN curl -fsSL "https://github.com/hezzel/cora/archive/${CORA_COMMIT}.tar.gz" \
      | tar -xz --strip-components=1 \
 && ./gradlew --no-daemon -q :app:installDist -x test

# Z3, which Cora runs as `z3 -in`. Distribution packages lag behind the >= 4.13 that Cora needs,
# so use the upstream release build, checked against its published digest.
FROM docker.io/library/eclipse-temurin:21-jdk AS z3
ARG Z3_VERSION=4.15.4
ARG Z3_SHA256=a41b690e89c343931471506cdc6d957b6044a200fd2d240cc017432afdff7d3e
RUN apt-get update -qq && apt-get install -y -qq unzip > /dev/null \
 && curl -fsSL -o /tmp/z3.zip \
      "https://github.com/Z3Prover/z3/releases/download/z3-${Z3_VERSION}/z3-${Z3_VERSION}-x64-glibc-2.39.zip" \
 && echo "${Z3_SHA256}  /tmp/z3.zip" | sha256sum -c - \
 && unzip -q /tmp/z3.zip -d /tmp \
 && mv /tmp/z3-${Z3_VERSION}-x64-glibc-2.39/bin/z3 /usr/local/bin/z3

FROM docker.io/library/eclipse-temurin:25-jre
# tini runs as PID 1 and reaps the Z3 processes Cora leaves behind when it exits; the JVM would
# otherwise inherit them as zombies.
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends tini > /dev/null \
 && rm -rf /var/lib/apt/lists/*
COPY --from=z3 /usr/local/bin/z3 /usr/local/bin/z3
COPY --from=cora /cora/app/build/install/app /opt/cora
COPY --from=build /src/target/rust2lctrs-*-server.jar /app/app.jar
RUN useradd --system --no-create-home demo
USER demo
WORKDIR /tmp
ENV CORA_BIN=/opt/cora/bin/app
ENTRYPOINT ["/usr/bin/tini", "--", "java", "-jar", "/app/app.jar"]
