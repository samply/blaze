#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"

restart "$COMPOSE_FILE"
cql/search.sh observation-17861-6
cql/search.sh observation-17861-6
cql/search.sh observation-17861-6

restart "$COMPOSE_FILE"
cql/search.sh observation-8310-5
cql/search.sh observation-8310-5
cql/search.sh observation-8310-5

restart "$COMPOSE_FILE"
cql/search.sh observation-72514-3
cql/search.sh observation-72514-3
cql/search.sh observation-72514-3

restart "$COMPOSE_FILE"
cql/search.sh observation-body-weight-10
cql/search.sh observation-body-weight-10
cql/search.sh observation-body-weight-10

restart "$COMPOSE_FILE"
cql/search.sh observation-body-weight-50
cql/search.sh observation-body-weight-50
cql/search.sh observation-body-weight-50

restart "$COMPOSE_FILE"
cql/search.sh observation-body-weight-100
cql/search.sh observation-body-weight-100
cql/search.sh observation-body-weight-100

#restart "$COMPOSE_FILE"
#cql/search.sh hemoglobin-date-age
#cql/search.sh hemoglobin-date-age
#cql/search.sh hemoglobin-date-age

#restart "$COMPOSE_FILE"
#cql/search.sh calcium-date-age
#cql/search.sh calcium-date-age
#cql/search.sh calcium-date-age

#restart "$COMPOSE_FILE"
#cql/search.sh condition-two
#cql/search.sh condition-two
#cql/search.sh condition-two

restart "$COMPOSE_FILE"
cql/search.sh condition-ten-frequent
cql/search.sh condition-ten-frequent
cql/search.sh condition-ten-frequent

restart "$COMPOSE_FILE"
cql/search.sh condition-ten-rare
cql/search.sh condition-ten-rare
cql/search.sh condition-ten-rare

#restart "$COMPOSE_FILE"
#cql/search.sh condition-50-rare
#cql/search.sh condition-50-rare
#cql/search.sh condition-50-rare

#restart "$COMPOSE_FILE"
#cql/search.sh condition-all
#cql/search.sh condition-all
#cql/search.sh condition-all

#restart "$COMPOSE_FILE"
#cql/search.sh inpatient-stress
#cql/search.sh inpatient-stress
#cql/search.sh inpatient-stress
