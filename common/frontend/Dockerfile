# Build context: repo root (design/). docker build -f common/frontend/Dockerfile .
FROM node:20-slim AS build
WORKDIR /src
COPY common/frontend ./common/frontend
COPY modules ./modules
WORKDIR /src/common/frontend
RUN npm install --no-audit --no-fund
# Module frontend folders live outside this project root; Node/Vite resolve
# bare imports (react, react-router-dom) by walking up for node_modules, so
# make it reachable there too (same fix as the local dev junction).
RUN ln -s /src/common/frontend/node_modules /src/modules/node_modules
RUN npm run build

FROM nginx:1.27-alpine AS runtime
RUN apk add --no-cache bash \
 && addgroup -g 2100 tsuser \
 && adduser -D -u 2100 -G tsuser -h /home/tsuser -s /sbin/nologin tsuser \
 && mkdir -p /etc/opzhub/certs/internal /etc/opzhub/certs/public \
             /var/lib/opzhub/run/svc /var/lib/opzhub/run/locks /var/lib/opzhub/tmp \
             /var/lib/opzhub/data/staging /var/lib/opzhub/data/web-cache \
             /var/lib/opzhub/backup/db /var/log/opzhub/jobs /var/log/opzhub/svc \
 && chown -R tsuser:tsuser /etc/opzhub /var/lib/opzhub /var/log/opzhub /home/tsuser /var/cache/nginx /run

USER tsuser
ENV HOME=/home/tsuser
WORKDIR /home/tsuser/opzhub

COPY --chown=tsuser:tsuser infra/wrappers ./infra/wrappers
COPY --chown=tsuser:tsuser gateway/web-nginx.conf.template ./gateway/web-nginx.conf
COPY --from=build --chown=tsuser:tsuser /src/common/frontend/dist ./web/dist

ENTRYPOINT ["bash", "infra/wrappers/run-web.sh"]
