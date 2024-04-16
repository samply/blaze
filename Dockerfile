FROM mcr.microsoft.com/dotnet/sdk:6.0@sha256:3bfaf0ce4417fb6c63d9a6ac1ddde8af9684c35a2b409815dd7ce15efe3d984b as fhir-packages

RUN dotnet tool install -g firely.terminal
RUN /root/.dotnet/tools/fhir install hl7.fhir.r4.core 4.0.1
RUN /root/.dotnet/tools/fhir install hl7.fhir.xver-extensions 0.1.0

FROM eclipse-temurin:17-jre-jammy@sha256:59e7f2166e0d4bd994e6dca36e7d217218e27b7173240627738b476c781395d3

RUN apt-get update && apt-get upgrade -y && \
    apt-get install libjemalloc2 -y && \
    apt-get purge wget libbinutils libctf0 libctf-nobfd0 libncurses6 -y && \
    apt-get autoremove -y && apt-get clean && \
    rm -rf /var/lib/apt/lists/

RUN groupadd -g 1001 blaze
RUN useradd -u 1001 -g 1001 --create-home blaze

RUN mkdir -p /app/data && chown 1001:1001 /app/data
COPY target/blaze-0.25.0-standalone.jar /app/
COPY --from=fhir-packages /root/.fhir/packages /home/blaze/.fhir/packages/
RUN chown -R 1001:1001 /home/blaze/.fhir

WORKDIR /app
USER 1001

ENV LD_PRELOAD="libjemalloc.so.2"
ENV STORAGE="standalone"
ENV INDEX_DB_DIR="/app/data/index"
ENV TRANSACTION_DB_DIR="/app/data/transaction"
ENV RESOURCE_DB_DIR="/app/data/resource"
ENV ADMIN_INDEX_DB_DIR="/app/data/admin-index"
ENV ADMIN_TRANSACTION_DB_DIR="/app/data/admin-transaction"
ENV ADMIN_RESOURCE_DB_DIR="/app/data/admin-resource"

CMD ["java", "-jar",  "blaze-0.25.0-standalone.jar"]
