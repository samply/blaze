FROM clojure:openjdk-11-tools-deps as build

COPY . /build/

WORKDIR /build
RUN clojure -A:depstar -m hf.depstar.uberjar target/blaze-standalone.jar

RUN mkdir -p /app/data

FROM gcr.io/distroless/java:11

WORKDIR /app

COPY --from=build --chown=nonroot:nonroot /app ./
COPY --from=build --chown=nonroot:nonroot /build/target/ ./

USER nonroot
EXPOSE 8080
ENV DB_DIR="/app/data/db"
CMD ["blaze-standalone.jar", "-m", "blaze.core"]
