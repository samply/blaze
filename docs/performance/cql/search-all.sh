#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"

restart "$compose_file"
cql/search.sh observation-17861-6
cql/search.sh observation-17861-6 false
cql/search.sh observation-17861-6 false
cql/search.sh observation-17861-6 false

restart "$compose_file"
cql/search.sh observation-8310-5
cql/search.sh observation-8310-5 false
cql/search.sh observation-8310-5 false
cql/search.sh observation-8310-5 false

restart "$compose_file"
cql/search.sh observation-72514-3
cql/search.sh observation-72514-3 false
cql/search.sh observation-72514-3 false
cql/search.sh observation-72514-3 false

restart "$compose_file"
cql/search.sh observation-body-weight-10
cql/search.sh observation-body-weight-10 false
cql/search.sh observation-body-weight-10 false
cql/search.sh observation-body-weight-10 false

restart "$compose_file"
cql/search.sh observation-body-weight-50
cql/search.sh observation-body-weight-50 false
cql/search.sh observation-body-weight-50 false
cql/search.sh observation-body-weight-50 false

restart "$compose_file"
cql/search.sh observation-body-weight-100
cql/search.sh observation-body-weight-100 false
cql/search.sh observation-body-weight-100 false
cql/search.sh observation-body-weight-100 false

#restart "$compose_file"
#cql/search.sh hemoglobin-date-age
#cql/search.sh hemoglobin-date-age
#cql/search.sh hemoglobin-date-age

#restart "$compose_file"
#cql/search.sh calcium-date-age
#cql/search.sh calcium-date-age
#cql/search.sh calcium-date-age

#restart "$compose_file"
#cql/search.sh condition-two
#cql/search.sh condition-two
#cql/search.sh condition-two

restart "$compose_file"
cql/search.sh condition-ten-frequent
cql/search.sh condition-ten-frequent false
cql/search.sh condition-ten-frequent false
cql/search.sh condition-ten-frequent false

restart "$compose_file"
cql/search.sh condition-ten-rare
cql/search.sh condition-ten-rare false
cql/search.sh condition-ten-rare false
cql/search.sh condition-ten-rare false

#restart "$compose_file"
#cql/search.sh condition-50-rare
#cql/search.sh condition-50-rare
#cql/search.sh condition-50-rare

restart "$compose_file"
cql/search.sh condition-all
cql/search.sh condition-all false
cql/search.sh condition-all false
cql/search.sh condition-all false

restart "$compose_file"
cql/search.sh inpatient-stress
cql/search.sh inpatient-stress false
cql/search.sh inpatient-stress false
cql/search.sh inpatient-stress false

restart "$compose_file"
cql/search.sh medication-1
cql/search.sh medication-1 false
cql/search.sh medication-1 false
cql/search.sh medication-1 false

restart "$compose_file"
cql/search.sh medication-7
cql/search.sh medication-7 false
cql/search.sh medication-7 false
cql/search.sh medication-7 false

restart "$compose_file"
cql/search.sh medication-ten
cql/search.sh medication-ten false
cql/search.sh medication-ten false
cql/search.sh medication-ten false
