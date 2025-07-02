FROM eclipse-temurin:21.0.7_6-jre-noble@sha256:862bb9fb9cf8272e02d07b44bf0812ed765c090aec572a18ab9d2e94be8d4bfd

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install libjemalloc2 -y && \
    apt-get purge wget libncurses6 -y && \
    apt-get autoremove -y && apt-get clean && \
    rm -rf /var/lib/apt/lists/

RUN mkdir -p /app/data && chown 1001:1001 /app/data
COPY target/blaze-1.0.3-standalone.jar /app/

WORKDIR /app
USER 1001

ENV LD_PRELOAD="libjemalloc.so.2"
ENV STORAGE="standalone"
ENV INDEX_DB_DIR="/app/data/index"
ENV TRANSACTION_DB_DIR="/app/data/transaction"
ENV RESOURCE_DB_DIR="/app/data/resource"
ENV ADMIN_INDEX_DB_DIR="/app/data/admin-index"
ENV ADMIN_TRANSACTION_DB_DIR="/app/data/admin-transaction"

CMD ["java", "-jar",  "blaze-1.0.3-standalone.jar"]
