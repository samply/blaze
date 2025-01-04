del(.suites[].tests[] |
select(
 .name == "validation-simple-codeableconcept-bad-code" or
 .name == "validation-simple-code-bad-import" or
 .name == "validation-simple-coding-bad-import" or
 .name == "validation-simple-codeableconcept-bad-import" or
 .name == "validation-simple-coding-bad-system" or
 .name == "validation-simple-coding-bad-system2" or
 .name == "validation-simple-coding-bad-system-local" or
 .name == "validation-simple-coding-no-system" or
 .name == "validation-simple-codeableconcept-bad-system" or
 .name == "validation-simple-coding-bad-version1" or
 .name == "validation-simple-codeableconcept-bad-version1" or
 .name == "validation-simple-codeableconcept-bad-version2"
))
