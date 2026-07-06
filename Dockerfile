FROM eclipse-temurin:25.0.3_9-jre-noble@sha256:2f1da100788559b397bcf48c736169ea5b070bde84e55f203bbee8e83d87a175

RUN set -eux; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends curl libjemalloc2 wget; \
    rm -rf /var/lib/apt/lists/

RUN mkdir -p /app/data && chown 1001:1001 /app/data
COPY target/blaze-1.10.1-standalone.jar /app/

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

CMD ["sh", "-c", "JAVA_TOOL_OPTIONS=\"${BASE_JAVA_TOOL_OPTIONS} ${JAVA_TOOL_OPTIONS}\" exec java -jar blaze-1.10.1-standalone.jar"]
