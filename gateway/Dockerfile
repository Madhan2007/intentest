# Build context: repo root (design/). docker build -f gateway/Dockerfile .
# Public door — the only internet-facing process (doc 00 §2, doc 09 §4).
FROM nginx:1.27-alpine AS runtime
RUN apk add --no-cache bash openssl \
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
COPY --chown=tsuser:tsuser gateway/nginx.conf.template ./gateway/nginx.conf

ENTRYPOINT ["bash", "infra/wrappers/run-nginx.sh"]
