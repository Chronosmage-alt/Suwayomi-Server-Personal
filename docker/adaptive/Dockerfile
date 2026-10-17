ARG RUNTIME_IMAGE=ghcr.io/suwayomi/suwayomi-server:preview
FROM ${RUNTIME_IMAGE}

ARG SOURCE_REVISION
LABEL org.opencontainers.image.source="https://github.com/Chronosmage-alt/Suwayomi-Server-Personal" \
      org.opencontainers.image.title="Suwayomi adaptive library updates test build" \
      org.opencontainers.image.revision="${SOURCE_REVISION}" \
      org.opencontainers.image.licenses="MPL-2.0"

COPY --chown=suwayomi:suwayomi server.jar /home/suwayomi/startup/tachidesk_latest.jar
COPY webui/ /opt/adaptive-webui/
COPY --chmod=755 start-adaptive.sh /usr/local/bin/start-adaptive.sh

ENV WEB_UI_FLAVOR=CUSTOM \
    WEB_UI_UPDATE_INTERVAL=0

# Retain the upstream tini entrypoint, Java, Xvfb and CEF setup.
CMD ["/usr/local/bin/start-adaptive.sh"]
