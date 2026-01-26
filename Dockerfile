FROM eclipse-temurin:25-jre-noble@sha256:ecbdcdbfae44ee61794a8ad36042b6b8e3c3124e5e9c171c3630fcd5ab856e33

RUN set -eux; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends curl libjemalloc2 wget; \
    rm -rf /var/lib/apt/lists/

RUN mkdir -p /app/data && chown 1001:1001 /app/data
COPY target/blaze-1.4.1-standalone.jar /app/

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

CMD ["sh", "-c", "JAVA_TOOL_OPTIONS=\"${BASE_JAVA_TOOL_OPTIONS} ${JAVA_TOOL_OPTIONS}\" exec java -jar blaze-1.4.1-standalone.jar"]
