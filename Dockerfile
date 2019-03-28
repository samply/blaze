FROM openjdk:8u191-jre-alpine3.9

COPY target/life-fhir-store-0.1-SNAPSHOT-standalone.jar /app/
COPY fhir /app/

WORKDIR /app

EXPOSE 80

CMD ["/bin/sh", "-c", "java $JVM_OPTS -jar life-fhir-store-0.1-SNAPSHOT-standalone.jar"]
