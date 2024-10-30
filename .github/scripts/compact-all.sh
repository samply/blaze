#!/bin/bash -e

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

"$SCRIPT_DIR/compact.sh" index search-param-value-index
"$SCRIPT_DIR/compact.sh" index resource-value-index
"$SCRIPT_DIR/compact.sh" index compartment-search-param-value-index
"$SCRIPT_DIR/compact.sh" index compartment-resource-type-index
"$SCRIPT_DIR/compact.sh" index active-search-params
"$SCRIPT_DIR/compact.sh" index tx-success-index
"$SCRIPT_DIR/compact.sh" index tx-error-index
"$SCRIPT_DIR/compact.sh" index t-by-instant-index
"$SCRIPT_DIR/compact.sh" index resource-as-of-index
"$SCRIPT_DIR/compact.sh" index type-as-of-index
"$SCRIPT_DIR/compact.sh" index system-as-of-index
"$SCRIPT_DIR/compact.sh" index patient-last-change-index
"$SCRIPT_DIR/compact.sh" index type-stats-index
"$SCRIPT_DIR/compact.sh" index system-stats-index
"$SCRIPT_DIR/compact.sh" index cql-bloom-filter
"$SCRIPT_DIR/compact.sh" index cql-bloom-filter-by-t
"$SCRIPT_DIR/compact.sh" transaction default
"$SCRIPT_DIR/compact.sh" resource default
