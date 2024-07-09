#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

COMPOSE_FILE="$1"

restart "$COMPOSE_FILE"
cql/search.sh observation-788-0
cql/search.sh observation-788-0 false
cql/search.sh observation-788-0 false

restart "$COMPOSE_FILE"
cql/search.sh observation-44261-6
cql/search.sh observation-44261-6 false
cql/search.sh observation-44261-6 false

restart "$COMPOSE_FILE"
cql/search.sh observation-72514-3
cql/search.sh observation-72514-3 false
cql/search.sh observation-72514-3 false

restart "$COMPOSE_FILE"
cql/search.sh condition-ten-frequent
cql/search.sh condition-ten-frequent false
cql/search.sh condition-ten-frequent false

restart "$COMPOSE_FILE"
cql/search.sh condition-ten-rare
cql/search.sh condition-ten-rare false
cql/search.sh condition-ten-rare false

restart "$COMPOSE_FILE"
cql/search.sh condition-all
cql/search.sh condition-all false
cql/search.sh condition-all false

restart "$COMPOSE_FILE"
cql/search.sh inpatient-stress
cql/search.sh inpatient-stress false
cql/search.sh inpatient-stress false
