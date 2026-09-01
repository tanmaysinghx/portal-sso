# syntax=docker/dockerfile:1

# Portal SSO ships as a single jar with the Angular console packaged inside it, so this produces one
# image with no sidecar and no static-file host to run alongside.
#
# The Angular build is split into its own stage rather than left to Maven's exec plugin. Maven
# invokes `npm run build` at generate-resources, which would mean putting Node into the Java build
# stage and rebuilding the whole frontend whenever a .java file changes. Building it separately lets
# Docker cache each half against its own inputs.

# ---------------------------------------------------------------------------------------------
# 1. Angular console
# ---------------------------------------------------------------------------------------------
FROM node:24-alpine AS client

WORKDIR /build/portal-client

# Node 24 specifically, to match the npm major that wrote package-lock.json. On node:22 (npm 10)
# `npm ci` fails with "Missing: @emnapi/core from lock file" — npm 10 and 11 record the
# platform-specific optional dependency tree differently, so an older image rejects a lock file a
# newer npm produced. Bumping the image is the right fix; regenerating the lock to suit an older
# toolchain than the project uses is not.
#
# Dependencies first, so a source-only change does not re-resolve the tree. `npm ci` rather than
# `npm install`: it installs exactly what package-lock.json pins and fails if the two disagree,
# which is what you want in a build you cannot watch.
COPY portal-client/package.json portal-client/package-lock.json ./
RUN npm ci

COPY portal-client/ ./
RUN npm run build

# ---------------------------------------------------------------------------------------------
# 2. Spring Boot jar
# ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS server

WORKDIR /build/portal-server

# The Maven wrapper is used rather than a maven base image so the build here resolves the same
# Maven version the project already pins.
COPY portal-server/.mvn/ .mvn/
COPY portal-server/mvnw portal-server/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY portal-server/src/ src/

# Where the resources plugin expects to find the built console, relative to portal-server.
COPY --from=client /build/portal-client/dist/ /build/portal-client/dist/

# exec.skip stops Maven re-running `npm run build` — there is no Node in this stage, and the output
# it would produce is already copied in above.
# Tests are skipped deliberately: the image build is not the place to discover a failure, and CI
# runs the full suite against a real database on every push.
RUN ./mvnw -B -q -DskipTests -Dexec.skip=true package \
    && cp target/portal-server-*.jar /build/portal-server.jar

# ---------------------------------------------------------------------------------------------
# 3. Runtime
# ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# curl is here only for HEALTHCHECK. Spring Boot has no CLI health probe, and checking that the port
# merely accepts a connection would report "healthy" while Liquibase is still migrating.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Runs unprivileged. An identity server is a poor thing to run as root.
RUN groupadd --system --gid 1001 portal \
    && useradd --system --uid 1001 --gid portal --home /app portal

WORKDIR /app
COPY --from=server --chown=portal:portal /build/portal-server.jar app.jar

USER portal
EXPOSE 8080

# readiness rather than the aggregate health group: it reports UP only once the application is
# actually able to serve, which on first boot is after Liquibase has finished. start-period covers
# the migration window without marking the container unhealthy while it works.
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

# Container memory limits are what the JVM should size the heap against, and MaxRAMPercentage is how
# it is told to. Without it the JVM assumes it may use the whole host.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
