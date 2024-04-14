#!/bin/sh -e

DIR="$1"
LEVEL="errors"

java -jar validator_cli.jar -version 4.0.1 \
  -level "$LEVEL" \
  -tx n/a \
  -ig resources/blaze/job/re_index/CodeSystem-JobType.json \
  -ig resources/blaze/job/re_index/CodeSystem-ReIndexJobParameter.json \
  -ig resources/blaze/job/re_index/StructureDefinition-ReIndexJob.json \
  "$DIR"
