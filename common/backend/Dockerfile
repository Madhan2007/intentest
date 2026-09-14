# Build context: repo root (design/). docker build -f common/backend/Dockerfile .
# Non-root runtime per doc 15; entrypoint is the already-implemented wrapper.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY common/backend ./common/backend
COPY modules ./modules
COPY apps ./apps
COPY platform ./platform
WORKDIR /src/common/backend
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update -qq \
 && apt-get install -y --no-install-recommends postgresql-client \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -g 2100 tsuser \
 && useradd -u 2100 -g 2100 -m -d /home/tsuser -s /usr/sbin/nologin tsuser \
 && mkdir -p /etc/opzhub/certs/internal /etc/opzhub/certs/public \
             /var/lib/opzhub/run/svc /var/lib/opzhub/run/locks /var/lib/opzhub/tmp \
             /var/lib/opzhub/data/staging /var/lib/opzhub/data/web-cache \
             /var/lib/opzhub/backup/db /var/log/opzhub/jobs /var/log/opzhub/svc \
 && chown -R tsuser:tsuser /etc/opzhub /var/lib/opzhub /var/log/opzhub /home/tsuser

USER tsuser
ENV HOME=/home/tsuser
WORKDIR /home/tsuser/opzhub

COPY --chown=tsuser:tsuser infra/wrappers ./infra/wrappers
COPY --chown=tsuser:tsuser platform ./platform
COPY --chown=tsuser:tsuser apps ./apps
COPY --from=build --chown=tsuser:tsuser /src/common/backend/target/opzhub-be-app.jar ./opzhub-be-app.jar

ENTRYPOINT ["bash", "infra/wrappers/run-opzhub-engine.sh"]
