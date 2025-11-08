#!/bin/bash -e

script_dir="$(dirname "$(readlink -f "$0")")"

compose_file="$1"

echo "Simple Code Search:"
"$script_dir/simple-code-search.sh" "$compose_file"

echo "Multiple Code Search:"
"$script_dir/multiple-code-search.sh" "$compose_file"

echo "Multiple Search Parameter Search:"
"$script_dir/observation-final-category-multiple-codes-search.sh" "$compose_file"

echo "Code and Value Search:"
"$script_dir/code-value-search.sh" "$compose_file"

echo "Code and Date Search:"
"$script_dir/code-date-search.sh" "$compose_file"

echo "Category and Date Search:"
"$script_dir/category-date-search.sh" "$compose_file"

echo "Code and Patient Search:"
"$script_dir/code-patient-search.sh" "$compose_file"

echo "Multiple Codes and Patient Search:"
"$script_dir/multiple-codes-patient-search.sh" "$compose_file"

echo "Code, Date, and Patient Search:"
"$script_dir/code-date-patient-search.sh" "$compose_file"

echo "Simple Date Search:"
"$script_dir/simple-date-search.sh" "$compose_file"

echo "Patient Date Search:"
"$script_dir/patient-date-search.sh" "$compose_file"
