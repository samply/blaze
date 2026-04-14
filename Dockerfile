FROM eclipse-temurin:25.0.2_10-jre-noble@sha256:a051234f864d7ab78bf0188c3c540ac06c711a3b566f00f246be37073cc99dce

RUN set -eux; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends curl libjemalloc2 wget; \
    rm -rf /var/lib/apt/lists/

RUN mkdir -p /app/data && chown 1001:1001 /app/data
COPY target/blaze-1.7.0-standalone.jar /app/

WORKDIR /app
USER 1001

ENV LD_PRELOAD="libjemalloc.so.2"
ENV BASE_JAVA_TOOL_OPTIONS="-XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"
ENV STORAGE="standalone"
ENV INDEX_DB_DIR="/app/data/index"
ENV TRANSACTION_DB_DIR="/app/data/transaction"
ENV RESOURCE_DB_DIR="/app/data/resource"
ENV ADMIN_INDEX_DB_DIR="/app/data/admin-index"
ENV ADMIN_TRANSACTION_DB_DIR="/app/data/admin-transaction"

CMD ["sh", "-c", "JAVA_TOOL_OPTIONS=\"${BASE_JAVA_TOOL_OPTIONS} ${JAVA_TOOL_OPTIONS}\" exec java -jar blaze-1.7.0-standalone.jar"]
