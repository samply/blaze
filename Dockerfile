# Update the SHA by calling crane digest eclipse-temurin:17-jre-jammy
FROM eclipse-temurin:17-jre-jammy@sha256:5c2c306ee7a4bee38be4b26dc652e8523e66bf679ca736c3876b87b2b2335e91

RUN apt-get update && apt-get upgrade -y && \
    apt-get install libjemalloc2 -y && \
    apt-get purge wget libbinutils libctf0 libctf-nobfd0 libncurses6 -y && \
    apt-get autoremove -y && apt-get clean && \
    rm -rf /var/lib/apt/lists/

RUN mkdir -p /app/data && chown 1001:1001 /app/data
COPY target/blaze-0.24.1-standalone.jar /app/

WORKDIR /app
USER 1001

ENV LD_PRELOAD="libjemalloc.so.2"
ENV STORAGE="standalone"
ENV INDEX_DB_DIR="/app/data/index"
ENV TRANSACTION_DB_DIR="/app/data/transaction"
ENV RESOURCE_DB_DIR="/app/data/resource"

CMD ["java", "-jar",  "blaze-0.24.1-standalone.jar"]
