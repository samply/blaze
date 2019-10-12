FROM clojure:openjdk-11-tools-deps as build

COPY . /build/

WORKDIR /build
RUN clojure -A:depstar -m hf.depstar.uberjar target/blaze-standalone.jar

FROM openjdk:11.0.4-jre

COPY --from=build /build/target/blaze-standalone.jar /app/

WORKDIR /app

CMD ["/bin/bash", "-c", "java $JVM_OPTS -jar blaze-standalone.jar -m blaze.core"]
