del(.suites[].tests[] |
select(
 .name == "parameters-expand-all-definitions2" or
 .name == "parameters-expand-all-property" or
 .name == "parameters-expand-enum-definitions2" or
 .name == "parameters-expand-enum-definitions3" or
 .name == "parameters-expand-isa-definitions2" or
 .name == "parameters-expand-enum-property" or
 .name == "parameters-expand-isa-property"
))
