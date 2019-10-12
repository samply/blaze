FROM clojure:openjdk-11-tools-deps as build

COPY . /build/

WORKDIR /build
RUN ./uberjar.sh

FROM openjdk:11.0.4-jre

COPY --from=build /build/target/blaze-0.7.0-alpha3-standalone.jar /app/

WORKDIR /app

CMD ["/bin/bash", "-c", "java $JVM_OPTS -jar blaze-0.7.0-alpha3-standalone.jar -m blaze.core"]
