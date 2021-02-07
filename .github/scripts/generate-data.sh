#!/usr/bin/env sh

curl -sLO https://github.com/synthetichealth/synthea/releases/download/v2.7.0/synthea-with-dependencies.jar

java -jar synthea-with-dependencies.jar -s 93254876 -p 100
