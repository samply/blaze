#!/bin/bash
set -euo pipefail

script_dir="$(dirname "$(readlink -f "$0")")"
. "$script_dir/util.sh"

compose_file="$1"

restart "$compose_file"
profile "$compose_file" "observation-788-0" search-repeatedly observation-788-0 3

restart "$compose_file"
profile "$compose_file" "observation-44261-6" search-repeatedly observation-44261-6 3

restart "$compose_file"
profile "$compose_file" "observation-72514-3" search-repeatedly observation-72514-3 3

restart "$compose_file"
profile "$compose_file" "condition-ten-frequent" search-repeatedly condition-ten-frequent 3

restart "$compose_file"
profile "$compose_file" "condition-ten-rare" search-repeatedly condition-ten-rare 3

restart "$compose_file"
profile "$compose_file" "condition-all" search-repeatedly condition-all 3

restart "$compose_file"
profile "$compose_file" "inpatient-stress" search-repeatedly inpatient-stress 3
