FROM clojure:openjdk-11-tools-deps-1.10.1.727 as build

COPY . /build/

WORKDIR /build
RUN clojure -X:depstar uberjar :jar target/blaze-standalone.jar

RUN mkdir -p /app/data

FROM gcr.io/distroless/java-debian10:11

WORKDIR /app

COPY --from=build --chown=nonroot:nonroot /app ./
COPY --from=build --chown=nonroot:nonroot /build/target/ ./

USER nonroot
ENV STORAGE="standalone"
ENV INDEX_DB_DIR="/app/data/index"
ENV TRANSACTION_DB_DIR="/app/data/transaction"
ENV RESOURCE_DB_DIR="/app/data/resource"
CMD ["blaze-standalone.jar", "-m", "blaze.core"]
