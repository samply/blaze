FROM eclipse-temurin:21.0.7_6-jre-alpine@sha256:8728e354e012e18310faa7f364d00185277dec741f4f6d593af6c61fc0eb15fd

RUN apk add jemalloc

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

HEALTHCHECK --start-period=30s CMD wget --spider http://localhost:8080/health

CMD ["java", "-jar",  "blaze-1.0.3-standalone.jar"]
