#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"

restart "$compose_file"
profile "$compose_file" "observation-17861-6" search-repeatedly observation-17861-6 4

restart "$compose_file"
profile "$compose_file" "observation-8310-5" search-repeatedly observation-8310-5 4

restart "$compose_file"
profile "$compose_file" "observation-72514-3" search-repeatedly observation-72514-3 4

restart "$compose_file"
profile "$compose_file" "observation-body-weight-10" search-repeatedly observation-body-weight-10 4

restart "$compose_file"
profile "$compose_file" "observation-body-weight-50" search-repeatedly observation-body-weight-50 4

restart "$compose_file"
profile "$compose_file" "observation-body-weight-100" search-repeatedly observation-body-weight-100 4

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
profile "$compose_file" "condition-ten-frequent" search-repeatedly condition-ten-frequent 4

restart "$compose_file"
profile "$compose_file" "condition-ten-rare" search-repeatedly condition-ten-rare 4

restart "$compose_file"
profile "$compose_file" "condition-ten-frequent-vs" search-repeatedly condition-ten-frequent-vs 4

restart "$compose_file"
profile "$compose_file" "condition-ten-rare-vs" search-repeatedly condition-ten-rare-vs 4

restart "$compose_file"
profile "$compose_file" "condition-all" search-repeatedly condition-all 4

restart "$compose_file"
profile "$compose_file" "inpatient-stress" search-repeatedly inpatient-stress 4

restart "$compose_file"
profile "$compose_file" "medication-1" search-repeatedly medication-1 4

restart "$compose_file"
profile "$compose_file" "medication-7" search-repeatedly medication-7 4

restart "$compose_file"
profile "$compose_file" "medication-ten" search-repeatedly medication-ten 4

restart "$compose_file"
profile "$compose_file" "cardiometabolic-risk" search-repeatedly cardiometabolic-risk 4
