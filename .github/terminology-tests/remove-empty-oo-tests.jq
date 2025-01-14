del(.suites[].tests[] |
select(
 .name == "validation-simple-code-bad-valueSet" or
 .name == "validation-simple-coding-bad-valueSet" or
 .name == "validation-simple-codeableconcept-bad-valueSet"
))
