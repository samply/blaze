# Update the SHA by calling crane digest eclipse-temurin:17-jre-jammy
FROM eclipse-temurin:17-jre-jammy@sha256:6785f3c419951f8ba16448e564d0f7f9aedffb6e8ae583ffc4c1de38431fd374

RUN apt-get update && apt-get upgrade -y && \
    apt-get install libjemalloc2 -y && \
    apt-get purge wget libbinutils libctf0 libctf-nobfd0 libncurses6 -y && \
    apt-get autoremove -y && apt-get clean && \
    rm -rf /var/lib/apt/lists/

RUN mkdir -p /app/data && chown 1001:1001 /app/data
COPY target/blaze-0.22.2-standalone.jar /app/

WORKDIR /app
USER 1001

ENV LD_PRELOAD="libjemalloc.so.2"
ENV STORAGE="standalone"
ENV INDEX_DB_DIR="/app/data/index"
ENV TRANSACTION_DB_DIR="/app/data/transaction"
ENV RESOURCE_DB_DIR="/app/data/resource"

CMD ["java", "-jar",  "blaze-0.22.2-standalone.jar"]
