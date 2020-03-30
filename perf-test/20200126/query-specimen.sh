#!/bin/sh

#BASE_URL="http://3.123.2.138:8080/fhir"
BASE_URL="http://blaze-test-host:8080/fhir"
#BASE_URL="http://localhost:8080/fhir"

# about 150 ms with 32M Specimen
# _count=50 about 200 ms with 391M Specimen on AWS m5a.4xlarge and gp2 SSD
# _count=50 about 600 ms with 391M Specimen on blaze-test-host
# _count=500 about 1.3 s with 391M Specimen on AWS m5a.4xlarge and gp2 SSD

# Git hash 96dbea28
# _count=50 about 500 ms with 500M Specimen on blaze-test-host
# _count=500 about 5 s with 500M Specimen on blaze-test-host
# _count=500 about 1 s with 500M Specimen on blaze-test-host (second run, cached)

# Git hash 37684a78
# _count=50 about 500 ms with 800M Specimen on blaze-test-host
# _count=500 about 5 s with 800M Specimen on blaze-test-host
# _count=500 about 1.2 s with 800M Specimen on blaze-test-host (second run, cached)

# 24.02.2020
# _count=50 about 130 ms with 100M Specimen on blaze-test-host (db-size < mem-size)
# _count=500 about 1.2 s with 100M Specimen on blaze-test-host (db-size < mem-size)
for code in {10..90}; do

  BODY_SITE="urn:oid:2.16.840.1.113883.6.43.1|C${code}.3"
  TYPE="https://fhir.bbmri.de/CodeSystem/SampleMaterialType|dna"
  URL="${BASE_URL}/Specimen?bodysite=${BODY_SITE}&type=${TYPE}&_count=500"

  echo $URL

  time curl -s $URL | jq '.entry | length'

done
