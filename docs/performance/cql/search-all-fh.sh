#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"

restart "$compose_file"
cql/search.sh observation-788-0
cql/search.sh observation-788-0 false
cql/search.sh observation-788-0 false

restart "$compose_file"
cql/search.sh observation-44261-6
cql/search.sh observation-44261-6 false
cql/search.sh observation-44261-6 false

restart "$compose_file"
cql/search.sh observation-72514-3
cql/search.sh observation-72514-3 false
cql/search.sh observation-72514-3 false

restart "$compose_file"
cql/search.sh condition-ten-frequent
cql/search.sh condition-ten-frequent false
cql/search.sh condition-ten-frequent false

restart "$compose_file"
cql/search.sh condition-ten-rare
cql/search.sh condition-ten-rare false
cql/search.sh condition-ten-rare false

restart "$compose_file"
cql/search.sh condition-all
cql/search.sh condition-all false
cql/search.sh condition-all false

restart "$compose_file"
cql/search.sh inpatient-stress
cql/search.sh inpatient-stress false
cql/search.sh inpatient-stress false
