FROM eclipse-temurin:25-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce

LABEL org.opencontainers.image.title="Blaze"
LABEL org.opencontainers.image.description="A FHIR® Server with internal, fast CQL Evaluation Engine"
LABEL org.opencontainers.image.url="https://blaze-server.org"
LABEL org.opencontainers.image.documentation="https://blaze-server.org"
LABEL org.opencontainers.image.source="https://github.com/samply/blaze"
LABEL org.opencontainers.image.licenses="Apache-2.0"

RUN set -eux; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends libjemalloc2 wget; \
    rm -rf /var/lib/apt/lists/

RUN mkdir -p /app/data && chown 1001:1001 /app/data
COPY target/blaze-standalone.jar /app/

WORKDIR /app
USER 1001

# The user 1001 has no passwd entry, so HOME would default to `/`. The
# org.hl7.fhir validator builds paths like `$HOME/.fhir/fhir-settings.json`
# and fails on a root home directory.
ENV HOME="/app/data"
ENV LD_PRELOAD="libjemalloc.so.2"
ENV BASE_JAVA_TOOL_OPTIONS="-XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"
ENV STORAGE="standalone"
ENV INDEX_DB_DIR="/app/data/index"
ENV TRANSACTION_DB_DIR="/app/data/transaction"
ENV RESOURCE_DB_DIR="/app/data/resource"
ENV ADMIN_INDEX_DB_DIR="/app/data/admin-index"
ENV ADMIN_TRANSACTION_DB_DIR="/app/data/admin-transaction"

CMD ["sh", "-c", "JAVA_TOOL_OPTIONS=\"${BASE_JAVA_TOOL_OPTIONS} ${JAVA_TOOL_OPTIONS}\" exec java -jar blaze-standalone.jar"]
