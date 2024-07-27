#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"

restart "$COMPOSE_FILE"
cql/search.sh observation-17861-6
cql/search.sh observation-17861-6 false
cql/search.sh observation-17861-6 false
cql/search.sh observation-17861-6 false

restart "$COMPOSE_FILE"
cql/search.sh observation-8310-5
cql/search.sh observation-8310-5 false
cql/search.sh observation-8310-5 false
cql/search.sh observation-8310-5 false

restart "$COMPOSE_FILE"
cql/search.sh observation-72514-3
cql/search.sh observation-72514-3 false
cql/search.sh observation-72514-3 false
cql/search.sh observation-72514-3 false

restart "$COMPOSE_FILE"
cql/search.sh observation-body-weight-10
cql/search.sh observation-body-weight-10 false
cql/search.sh observation-body-weight-10 false
cql/search.sh observation-body-weight-10 false

restart "$COMPOSE_FILE"
cql/search.sh observation-body-weight-50
cql/search.sh observation-body-weight-50 false
cql/search.sh observation-body-weight-50 false
cql/search.sh observation-body-weight-50 false

restart "$COMPOSE_FILE"
cql/search.sh observation-body-weight-100
cql/search.sh observation-body-weight-100 false
cql/search.sh observation-body-weight-100 false
cql/search.sh observation-body-weight-100 false

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
cql/search.sh condition-ten-frequent false
cql/search.sh condition-ten-frequent false
cql/search.sh condition-ten-frequent false

restart "$COMPOSE_FILE"
cql/search.sh condition-ten-rare
cql/search.sh condition-ten-rare false
cql/search.sh condition-ten-rare false
cql/search.sh condition-ten-rare false

#restart "$COMPOSE_FILE"
#cql/search.sh condition-50-rare
#cql/search.sh condition-50-rare
#cql/search.sh condition-50-rare

restart "$COMPOSE_FILE"
cql/search.sh condition-all
cql/search.sh condition-all false
cql/search.sh condition-all false
cql/search.sh condition-all false

restart "$COMPOSE_FILE"
cql/search.sh inpatient-stress
cql/search.sh inpatient-stress false
cql/search.sh inpatient-stress false
cql/search.sh inpatient-stress false

restart "$COMPOSE_FILE"
cql/search.sh medication-1
cql/search.sh medication-1 false
cql/search.sh medication-1 false
cql/search.sh medication-1 false

restart "$COMPOSE_FILE"
cql/search.sh medication-7
cql/search.sh medication-7 false
cql/search.sh medication-7 false
cql/search.sh medication-7 false

restart "$COMPOSE_FILE"
cql/search.sh medication-ten
cql/search.sh medication-ten false
cql/search.sh medication-ten false
cql/search.sh medication-ten false
