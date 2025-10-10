FROM eclipse-temurin:21.0.8_9-jre-noble@sha256:80a2bd1900737b87398d83f52024e80cb8d4a133f060482bf60be7c5d11003bd

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install libjemalloc2 -y && \
    apt-get purge wget libncurses6 -y && \
    apt-get autoremove -y && apt-get clean && \
    rm -rf /var/lib/apt/lists/

RUN mkdir -p /app/data && chown 1001:1001 /app/data
COPY target/blaze-1.1.2-standalone.jar /app/

WORKDIR /app
USER 1001

ENV LD_PRELOAD="libjemalloc.so.2"
ENV STORAGE="standalone"
ENV INDEX_DB_DIR="/app/data/index"
ENV TRANSACTION_DB_DIR="/app/data/transaction"
ENV RESOURCE_DB_DIR="/app/data/resource"
ENV ADMIN_INDEX_DB_DIR="/app/data/admin-index"
ENV ADMIN_TRANSACTION_DB_DIR="/app/data/admin-transaction"

CMD ["java", "-jar",  "blaze-1.1.2-standalone.jar"]
