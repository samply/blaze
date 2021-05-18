FROM busybox as build

RUN mkdir -p /app/data

FROM gcr.io/distroless/java-debian10:11

COPY --from=build --chown=nonroot:nonroot /app /app
COPY --chown=nonroot:nonroot target/blaze-standalone.jar /app/

WORKDIR /app
USER nonroot

ENV STORAGE="standalone"
ENV INDEX_DB_DIR="/app/data/index"
ENV TRANSACTION_DB_DIR="/app/data/transaction"
ENV RESOURCE_DB_DIR="/app/data/resource"

CMD ["blaze-standalone.jar", "-m", "blaze.core"]
