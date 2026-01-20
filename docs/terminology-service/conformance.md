# Conformance

Blaze terminology service conformance is tested against the [FHIR Terminology Ecosystem IG Test Cases](https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/testcases.html). Currently passing test cases are marked in the `Pass` column.

## Test Cases

### Metadata

| Name        | Operation    | Pass |
|:------------|:-------------|:-----|
| `metadata`  | `$metadata`  |      |
| `term-caps` | `$term-caps` |      |

### Simple Cases

| Name                        | Operation | Pass |
|:----------------------------|:----------|:-----|
| `simple-expand-all`         | `$expand` | ✓    |
| `simple-expand-active`      | `$expand` | ✓    |
| `simple-expand-inactive`    | `$expand` | ✓    |
| `simple-expand-enum`        | `$expand` | ✓    |
| `simple-expand-enum-bad`    | `$expand` | ✓    |
| `simple-expand-isa`         | `$expand` | ✓    |
| `simple-expand-prop`        | `$expand` | ✓    |
| `simple-expand-regex`       | `$expand` | ✓    |
| `simple-expand-regex2`      | `$expand` | ✓    |
| `simple-expand-regexp-prop` | `$expand` | ✓    |
| `simple-lookup-1`           | `$lookup` |      |
| `simple-lookup-2`           | `$lookup` |      |

### Parameters

| Name                                  | Operation | Pass |
|:--------------------------------------|:----------|:-----|
| `parameters-expand-all-hierarchy`     | `$expand` |      |
| `parameters-expand-enum-hierarchy`    | `$expand` |      |
| `parameters-expand-isa-hierarchy`     | `$expand` |      |
| `parameters-expand-all-active`        | `$expand` |      |
| `parameters-expand-active-active`     | `$expand` |      |
| `parameters-expand-inactive-active`   | `$expand` |      |
| `parameters-expand-enum-active`       | `$expand` |      |
| `parameters-expand-isa-active`        | `$expand` |      |
| `parameters-expand-all-inactive`      | `$expand` |      |
| `parameters-expand-active-inactive`   | `$expand` |      |
| `parameters-expand-inactive-inactive` | `$expand` |      |
| `parameters-expand-enum-inactive`     | `$expand` |      |
| `parameters-expand-isa-inactive`      | `$expand` |      |
| `parameters-expand-all-designations`  | `$expand` |      |
| `parameters-expand-enum-designations` | `$expand` |      |
| `parameters-expand-isa-designations`  | `$expand` |      |
| `parameters-expand-all-definitions`   | `$expand` |      |
| `parameters-expand-enum-definitions`  | `$expand` |      |
| `parameters-expand-isa-definitions`   | `$expand` |      |
| `parameters-expand-all-definitions2`  | `$expand` |      |
| `parameters-expand-enum-definitions2` | `$expand` |      |
| `parameters-expand-enum-definitions3` | `$expand` |      |
| `parameters-expand-isa-definitions2`  | `$expand` |      |
| `parameters-expand-all-property`      | `$expand` |      |
| `parameters-expand-enum-property`     | `$expand` |      |
| `parameters-expand-isa-property`      | `$expand` |      |

### Language

| Name                                 | Operation | Pass |
|:-------------------------------------|:----------|:-----|
| `language-echo-en-none`              | `$expand` |      |
| `language-echo-de-none`              | `$expand` |      |
| `language-echo-en-multi-none`        | `$expand` |      |
| `language-echo-de-multi-none`        | `$expand` |      |
| `language-echo-en-en-param`          | `$expand` |      |
| `language-echo-en-en-vs`             | `$expand` |      |
| `language-echo-en-en-header`         | `$expand` |      |
| `language-echo-en-en-vslang`         | `$expand` |      |
| `language-echo-en-en-mixed`          | `$expand` |      |
| `language-echo-de-de-param`          | `$expand` |      |
| `language-echo-de-de-vs`             | `$expand` |      |
| `language-echo-de-de-header`         | `$expand` |      |
| `language-echo-en-multi-en-param`    | `$expand` |      |
| `language-echo-en-multi-en-vs`       | `$expand` |      |
| `language-echo-en-multi-en-header`   | `$expand` |      |
| `language-echo-de-multi-de-param`    | `$expand` |      |
| `language-echo-de-multi-de-vs`       | `$expand` |      |
| `language-echo-de-multi-de-header`   | `$expand` |      |
| `language-xform-en-multi-de-soft`    | `$expand` |      |
| `language-xform-en-multi-de-hard`    | `$expand` |      |
| `language-xform-en-multi-de-default` | `$expand` |      |
| `language-xform-de-multi-en-soft`    | `$expand` |      |
| `language-xform-de-multi-en-hard`    | `$expand` |      |
| `language-xform-de-multi-en-default` | `$expand` |      |
| `language-echo-en-designation`       | `$expand` |      |
| `language-echo-en-designations`      | `$expand` |      |

### Language 2

| Name                           | Operation        | Pass |
|:-------------------------------|:-----------------|:-----|
| `validation-right-de-en`       | `$validate-code` |      |
| `validation-right-de-ende-N`   | `$validate-code` |      |
| `validation-right-de-ende`     | `$validate-code` |      |
| `validation-right-de-none`     | `$validate-code` |      |
| `validation-right-en-en`       | `$validate-code` |      |
| `validation-right-en-ende-N`   | `$validate-code` |      |
| `validation-right-en-ende`     | `$validate-code` |      |
| `validation-right-en-none`     | `$validate-code` |      |
| `validation-right-none-en`     | `$validate-code` |      |
| `validation-right-none-ende-N` | `$validate-code` |      |
| `validation-right-none-ende`   | `$validate-code` |      |
| `validation-right-none-none`   | `$validate-code` |      |
| `validation-wrong-de-en`       | `$validate-code` |      |
| `validation-wrong-de-en-bad`   | `$validate-code` |      |
| `validation-wrong-de-ende-N`   | `$validate-code` |      |
| `validation-wrong-de-ende`     | `$validate-code` |      |
| `validation-wrong-de-none`     | `$validate-code` |      |
| `validation-wrong-en-en`       | `$validate-code` |      |
| `validation-wrong-en-ende-N`   | `$validate-code` |      |
| `validation-wrong-en-ende`     | `$validate-code` |      |
| `validation-wrong-en-none`     | `$validate-code` |      |
| `validation-wrong-none-en`     | `$validate-code` |      |
| `validation-wrong-none-ende-N` | `$validate-code` |      |
| `validation-wrong-none-ende`   | `$validate-code` |      |
| `validation-wrong-none-none`   | `$validate-code` |      |

### Extensions

| Name                                      | Operation           | Pass |
|:------------------------------------------|:--------------------|:-----|
| `extensions-echo-all`                     | `$expand`           |      |
| `extensions-echo-enumerated`              | `$expand`           |      |
| `extensions-echo-bad-supplement`          | `$expand`           |      |
| `validate-code-bad-supplement`            | `$validate-code`    |      |
| `validate-coding-bad-supplement`          | `$validate-code`    |      |
| `validate-coding-bad-supplement-url`      | `$cs-validate-code` |      |
| `validate-codeableconcept-bad-supplement` | `$validate-code`    |      |
| `validate-coding-good-supplement`         | `$validate-code`    | ✓    |
| `validate-coding-good2-supplement`        | `$validate-code`    |      |
| `validate-code-inactive-display`          | `$cs-validate-code` |      |
| `validate-code-inactive`                  | `$cs-validate-code` |      |

### Validation

| Name                                                    | Operation           | Pass |
|:--------------------------------------------------------|:--------------------|:-----|
| `validation-simple-code-good`                           | `$validate-code`    | ✓    |
| `validation-simple-code-implied-good`                   | `$validate-code`    | ✓    |
| `validation-simple-coding-good`                         | `$validate-code`    | ✓    |
| `validation-simple-codeableconcept-good`                | `$validate-code`    | ✓    |
| `validation-simple-code-bad-code`                       | `$validate-code`    |      |
| `validation-simple-code-implied-bad-code`               | `$validate-code`    |      |
| `validation-simple-coding-bad-code`                     | `$validate-code`    |      |
| `validation-simple-coding-bad-code-inactive`            | `$validate-code`    |      |
| `validation-simple-codeableconcept-bad-code`            | `$validate-code`    |      |
| `validation-simple-code-bad-valueSet`                   | `$validate-code`    |      |
| `validation-simple-coding-bad-valueSet`                 | `$validate-code`    |      |
| `validation-simple-codeableconcept-bad-valueSet`        | `$validate-code`    |      |
| `validation-simple-code-bad-import`                     | `$validate-code`    |      |
| `validation-simple-coding-bad-import`                   | `$validate-code`    |      |
| `validation-simple-codeableconcept-bad-import`          | `$validate-code`    |      |
| `validation-simple-code-bad-system`                     | `$validate-code`    |      |
| `validation-simple-coding-bad-system`                   | `$validate-code`    |      |
| `validation-simple-coding-bad-system2`                  | `$validate-code`    |      |
| `validation-simple-coding-bad-system-local`             | `$validate-code`    |      |
| `validation-simple-coding-no-system`                    | `$validate-code`    |      |
| `validation-simple-codeableconcept-bad-system`          | `$validate-code`    |      |
| `validation-simple-code-good-display`                   | `$validate-code`    | ✓    |
| `validation-simple-coding-good-display`                 | `$validate-code`    | ✓    |
| `validation-simple-codeableconcept-good-display`        | `$validate-code`    | ✓    |
| `validation-simple-code-bad-display`                    | `$validate-code`    |      |
| `validation-simple-code-bad-display-ws`                 | `$validate-code`    |      |
| `validation-simple-coding-bad-display`                  | `$validate-code`    |      |
| `validation-simple-codeableconcept-bad-display`         | `$validate-code`    |      |
| `validation-simple-code-bad-display-warning`            | `$validate-code`    |      |
| `validation-simple-coding-bad-display-warning`          | `$validate-code`    |      |
| `validation-simple-codeableconcept-bad-display-warning` | `$validate-code`    |      |
| `validation-simple-code-good-language`                  | `$validate-code`    | ✓    |
| `validation-simple-coding-good-language`                | `$validate-code`    | ✓    |
| `validation-simple-codeableconcept-good-language`       | `$validate-code`    | ✓    |
| `validation-simple-code-bad-language`                   | `$validate-code`    |      |
| `validation-simple-code-good-regex`                     | `$validate-code`    | ✓    |
| `validation-simple-code-bad-regex`                      | `$validate-code`    |      |
| `validation-simple-coding-bad-language`                 | `$validate-code`    |      |
| `validation-simple-coding-bad-language-header`          | `$validate-code`    |      |
| `validation-simple-coding-bad-language-vs`              | `$validate-code`    |      |
| `validation-simple-coding-bad-language-vslang`          | `$validate-code`    |      |
| `validation-simple-codeableconcept-bad-language`        | `$validate-code`    |      |
| `validation-complex-codeableconcept-full`               | `$validate-code`    |      |
| `validation-complex-codeableconcept-vsonly`             | `$validate-code`    |      |
| `validation-cs-code-good`                               | `$cs-validate-code` | ✓    |
| `validation-cs-code-bad-code`                           | `$cs-validate-code` |      |

### Version

| Name                                          | Operation        | Pass |
|:----------------------------------------------|:-----------------|:-----|
| `version-simple-code-bad-version1`            | `$validate-code` |      |
| `version-simple-coding-bad-version1`          | `$validate-code` |      |
| `version-simple-codeableconcept-bad-version1` | `$validate-code` |      |
| `version-simple-codeableconcept-bad-version2` | `$validate-code` |      |
| `version-simple-code-good-version`            | `$validate-code` | ✓    |
| `version-simple-coding-good-version`          | `$validate-code` | ✓    |
| `version-simple-codeableconcept-good-version` | `$validate-code` | ✓    |
| `version-version-profile-none`                | `$validate-code` |      |
| `version-version-profile-default`             | `$validate-code` |      |
| `validation-version-profile-coding`           | `$validate-code` | ✓    |
| `coding-vnn-vsnn`                             | `$validate-code` |      |
| `coding-v10-vs1w`                             | `$validate-code` |      |
| `coding-v10-vs1wb`                            | `$validate-code` |      |
| `coding-v10-vs10`                             | `$validate-code` |      |
| `coding-v10-vs20`                             | `$validate-code` |      |
| `coding-v10-vsbb`                             | `$validate-code` |      |
| `coding-v10-vsbb`                             | `$validate-code` |      |
| `coding-v10-vsnn`                             | `$validate-code` |      |
| `coding-vbb-vs10`                             | `$validate-code` |      |
| `coding-vbb-vsnn`                             | `$validate-code` |      |
| `coding-vnn-vs1w`                             | `$validate-code` |      |
| `coding-vnn-vs1wb`                            | `$validate-code` |      |
| `coding-vnn-vs10`                             | `$validate-code` |      |
| `coding-vnn-vsbb`                             | `$validate-code` |      |
| `coding-vnn-vsnn-default`                     | `$validate-code` |      |
| `coding-v10-vs1w-default`                     | `$validate-code` |      |
| `coding-v10-vs1wb-default`                    | `$validate-code` |      |
| `coding-v10-vs10-default`                     | `$validate-code` |      |
| `coding-v10-vs20-default`                     | `$validate-code` |      |
| `coding-v10-vsbb-default`                     | `$validate-code` |      |
| `coding-v10-vsnn-default`                     | `$validate-code` |      |
| `coding-vbb-vs10-default`                     | `$validate-code` |      |
| `coding-vbb-vsnn-default`                     | `$validate-code` |      |
| `coding-vnn-vs1w-default`                     | `$validate-code` |      |
| `coding-vnn-vs1wb-default`                    | `$validate-code` |      |
| `coding-vnn-vs10-default`                     | `$validate-code` |      |
| `coding-vnn-vsbb-default`                     | `$validate-code` |      |
| `coding-vnn-vsnn-check`                       | `$validate-code` |      |
| `coding-v10-vs1w-check`                       | `$validate-code` |      |
| `coding-v10-vs1wb-check`                      | `$validate-code` |      |
| `coding-v10-vs10-check`                       | `$validate-code` |      |
| `coding-v10-vs20-check`                       | `$validate-code` |      |
| `coding-v10-vsbb-check`                       | `$validate-code` |      |
| `coding-v10-vsnn-check`                       | `$validate-code` |      |
| `coding-vbb-vs10-check`                       | `$validate-code` |      |
| `coding-vbb-vsnn-check`                       | `$validate-code` |      |
| `coding-vnn-vs1w-check`                       | `$validate-code` |      |
| `coding-vnn-vs1wb-check`                      | `$validate-code` |      |
| `coding-vnn-vs10-check`                       | `$validate-code` |      |
| `coding-vnn-vsbb-check`                       | `$validate-code` |      |
| `coding-vnn-vsnn-force`                       | `$validate-code` |      |
| `coding-v10-vs1w-force`                       | `$validate-code` |      |
| `coding-v10-vs1wb-force`                      | `$validate-code` |      |
| `coding-v10-vs10-force`                       | `$validate-code` |      |
| `coding-v10-vs20-force`                       | `$validate-code` |      |
| `coding-v10-vsbb-force`                       | `$validate-code` |      |
| `coding-v10-vsnn-force`                       | `$validate-code` |      |
| `coding-vbb-vs10-force`                       | `$validate-code` |      |
| `coding-vbb-vsnn-force`                       | `$validate-code` |      |
| `coding-vnn-vs1w-force`                       | `$validate-code` |      |
| `coding-vnn-vs1wb-force`                      | `$validate-code` |      |
| `coding-vnn-vs10-force`                       | `$validate-code` |      |
| `coding-vnn-vsbb-force`                       | `$validate-code` |      |
| `codeableconcept-vnn-vsnn`                    | `$validate-code` |      |
| `codeableconcept-v10-vs1w`                    | `$validate-code` |      |
| `codeableconcept-v10-vs1wb`                   | `$validate-code` |      |
| `codeableconcept-v10-vs10`                    | `$validate-code` |      |
| `codeableconcept-v10-vs20`                    | `$validate-code` |      |
| `codeableconcept-v10-vsbb`                    | `$validate-code` |      |
| `codeableconcept-v10-vsbb`                    | `$validate-code` |      |
| `codeableconcept-v10-vsnn`                    | `$validate-code` |      |
| `codeableconcept-vbb-vs10`                    | `$validate-code` |      |
| `codeableconcept-vbb-vsnn`                    | `$validate-code` |      |
| `codeableconcept-vnn-vs1w`                    | `$validate-code` |      |
| `codeableconcept-vnn-vs1wb`                   | `$validate-code` |      |
| `codeableconcept-vnn-vs10`                    | `$validate-code` |      |
| `codeableconcept-vnn-vsbb`                    | `$validate-code` |      |
| `codeableconcept-vnn-vsnn-default`            | `$validate-code` |      |
| `codeableconcept-v10-vs1w-default`            | `$validate-code` |      |
| `codeableconcept-v10-vs1wb-default`           | `$validate-code` |      |
| `codeableconcept-v10-vs10-default`            | `$validate-code` |      |
| `codeableconcept-v10-vs20-default`            | `$validate-code` |      |
| `codeableconcept-v10-vsbb-default`            | `$validate-code` |      |
| `codeableconcept-v10-vsnn-default`            | `$validate-code` |      |
| `codeableconcept-vbb-vs10-default`            | `$validate-code` |      |
| `codeableconcept-vbb-vsnn-default`            | `$validate-code` |      |
| `codeableconcept-vnn-vs1w-default`            | `$validate-code` |      |
| `codeableconcept-vnn-vs1wb-default`           | `$validate-code` |      |
| `codeableconcept-vnn-vs10-default`            | `$validate-code` |      |
| `codeableconcept-vnn-vsbb-default`            | `$validate-code` |      |
| `codeableconcept-vnn-vsnn-check`              | `$validate-code` |      |
| `codeableconcept-v10-vs1w-check`              | `$validate-code` |      |
| `codeableconcept-v10-vs1wb-check`             | `$validate-code` |      |
| `codeableconcept-v10-vs10-check`              | `$validate-code` |      |
| `codeableconcept-v10-vs20-check`              | `$validate-code` |      |
| `codeableconcept-v10-vsbb-check`              | `$validate-code` |      |
| `codeableconcept-v10-vsnn-check`              | `$validate-code` |      |
| `codeableconcept-vbb-vs10-check`              | `$validate-code` |      |
| `codeableconcept-vbb-vsnn-check`              | `$validate-code` |      |
| `codeableconcept-vnn-vs1w-check`              | `$validate-code` |      |
| `codeableconcept-vnn-vs1wb-check`             | `$validate-code` |      |
| `codeableconcept-vnn-vs10-check`              | `$validate-code` |      |
| `codeableconcept-vnn-vsbb-check`              | `$validate-code` |      |
| `codeableconcept-vnn-vsnn-force`              | `$validate-code` |      |
| `codeableconcept-v10-vs1w-force`              | `$validate-code` |      |
| `codeableconcept-v10-vs1wb-force`             | `$validate-code` |      |
| `codeableconcept-v10-vs10-force`              | `$validate-code` |      |
| `codeableconcept-v10-vs20-force`              | `$validate-code` |      |
| `codeableconcept-v10-vsbb-force`              | `$validate-code` |      |
| `codeableconcept-v10-vsnn-force`              | `$validate-code` |      |
| `codeableconcept-vbb-vs10-force`              | `$validate-code` |      |
| `codeableconcept-vbb-vsnn-force`              | `$validate-code` |      |
| `codeableconcept-vnn-vs1w-force`              | `$validate-code` |      |
| `codeableconcept-vnn-vs1wb-force`             | `$validate-code` |      |
| `codeableconcept-vnn-vs10-force`              | `$validate-code` |      |
| `codeableconcept-vnn-vsbb-force`              | `$validate-code` |      |
| `code-vnn-vsnn`                               | `$validate-code` |      |
| `code-v10-vs1w`                               | `$validate-code` |      |
| `code-v10-vs1wb`                              | `$validate-code` |      |
| `code-v10-vs10`                               | `$validate-code` |      |
| `code-v10-vs20`                               | `$validate-code` |      |
| `code-v10-vsbb`                               | `$validate-code` |      |
| `code-v10-vsnn`                               | `$validate-code` |      |
| `code-vbb-vs10`                               | `$validate-code` |      |
| `code-vbb-vsnn`                               | `$validate-code` |      |
| `code-vnn-vs1w`                               | `$validate-code` |      |
| `code-vnn-vs1wb`                              | `$validate-code` |      |
| `code-vnn-vs10`                               | `$validate-code` |      |
| `code-vnn-vsbb`                               | `$validate-code` |      |
| `code-vnn-vsnn-default`                       | `$validate-code` |      |
| `code-v10-vs1w-default`                       | `$validate-code` |      |
| `code-v10-vs1wb-default`                      | `$validate-code` |      |
| `code-v10-vs10-default`                       | `$validate-code` |      |
| `code-v10-vs20-default`                       | `$validate-code` |      |
| `code-v10-vsbb-default`                       | `$validate-code` |      |
| `code-v10-vsnn-default`                       | `$validate-code` |      |
| `code-vbb-vs10-default`                       | `$validate-code` |      |
| `code-vbb-vsnn-default`                       | `$validate-code` |      |
| `code-vnn-vs1wb-default`                      | `$validate-code` |      |
| `code-vnn-vs10-default`                       | `$validate-code` |      |
| `code-vnn-vsbb-default`                       | `$validate-code` |      |
| `code-vnn-vsnn-check`                         | `$validate-code` |      |
| `code-v10-vs1w-check`                         | `$validate-code` |      |
| `code-v10-vs1wb-check`                        | `$validate-code` |      |
| `code-v10-vs10-check`                         | `$validate-code` |      |
| `code-v10-vs20-check`                         | `$validate-code` |      |
| `code-v10-vsbb-check`                         | `$validate-code` |      |
| `code-v10-vsnn-check`                         | `$validate-code` |      |
| `code-vbb-vs10-check`                         | `$validate-code` |      |
| `code-vbb-vsnn-check`                         | `$validate-code` |      |
| `code-vnn-vs1w-check`                         | `$validate-code` |      |
| `code-vnn-vs1wb-check`                        | `$validate-code` |      |
| `code-vnn-vs10-check`                         | `$validate-code` |      |
| `code-vnn-vsbb-check`                         | `$validate-code` |      |
| `code-vnn-vsnn-force`                         | `$validate-code` |      |
| `code-v10-vs1w-force`                         | `$validate-code` |      |
| `code-v10-vs1wb-force`                        | `$validate-code` |      |
| `code-v10-vs10-force`                         | `$validate-code` |      |
| `code-v10-vs20-force`                         | `$validate-code` |      |
| `code-v10-vsbb-force`                         | `$validate-code` |      |
| `code-v10-vsnn-force`                         | `$validate-code` |      |
| `code-vbb-vs10-force`                         | `$validate-code` |      |
| `code-vbb-vsnn-force`                         | `$validate-code` |      |
| `code-vnn-vs1w-force`                         | `$validate-code` |      |
| `code-vnn-vs1wb-force`                        | `$validate-code` |      |
| `code-vnn-vs10-force`                         | `$validate-code` |      |
| `code-vnn-vsbb-force`                         | `$validate-code` |      |
| `code-vnn-vsmix-1`                            | `$validate-code` |      |
| `code-vnn-vsmix-2`                            | `$validate-code` |      |
| `vs-expand-all-v`                             | `$expand`        |      |
| `vs-expand-all-v1`                            | `$expand`        |      |
| `vs-expand-all-v2`                            | `$expand`        |      |
| `vs-expand-v-mixed`                           | `$expand`        |      |
| `vs-expand-v-n-request`                       | `$expand`        |      |
| `vs-expand-v-w`                               | `$expand`        |      |
| `vs-expand-v-wb`                              | `$expand`        |      |
| `vs-expand-v1`                                | `$expand`        |      |
| `vs-expand-v2`                                | `$expand`        |      |
| `vs-expand-all-v-force`                       | `$expand`        |      |
| `vs-expand-all-v1-force`                      | `$expand`        |      |
| `vs-expand-all-v2-force`                      | `$expand`        |      |
| `vs-expand-v-mixed-force`                     | `$expand`        |      |
| `vs-expand-v-n-force-request`                 | `$expand`        |      |
| `vs-expand-v-w-force`                         | `$expand`        |      |
| `vs-expand-v-wb-force`                        | `$expand`        |      |
| `vs-expand-v1-force`                          | `$expand`        |      |
| `vs-expand-v2-force`                          | `$expand`        |      |
| `vs-expand-all-v-default`                     | `$expand`        |      |
| `vs-expand-all-v1-default`                    | `$expand`        |      |
| `vs-expand-all-v2-default`                    | `$expand`        |      |
| `vs-expand-v-mixed-default`                   | `$expand`        |      |
| `vs-expand-v-n-default-request`               | `$expand`        |      |
| `vs-expand-v-w-default`                       | `$expand`        |      |
| `vs-expand-v-wb-default`                      | `$expand`        |      |
| `vs-expand-v1-default`                        | `$expand`        |      |
| `vs-expand-v2-default`                        | `$expand`        |      |
| `vs-expand-all-v-check`                       | `$expand`        |      |
| `vs-expand-all-v1-check`                      | `$expand`        |      |
| `vs-expand-all-v2-check`                      | `$expand`        |      |
| `vs-expand-v-mixed-check`                     | `$expand`        |      |
| `vs-expand-v-n-check-request`                 | `$expand`        |      |
| `vs-expand-v-w-check`                         | `$expand`        |      |
| `vs-expand-v-wb-check`                        | `$expand`        |      |
| `vs-expand-v1-check`                          | `$expand`        |      |
| `vs-expand-v2-check`                          | `$expand`        |      |

### Fragment

| Name                                           | Operation        | Pass |
|:-----------------------------------------------|:-----------------|:-----|
| `validation-fragment-code-good`                | `$validate-code` | ✓    |
| `validation-fragment-coding-good`              | `$validate-code` | ✓    |
| `validation-fragment-codeableconcept-good`     | `$validate-code` | ✓    |
| `validation-fragment-code-bad-code`            | `$validate-code` |      |
| `validation-fragment-coding-bad-code`          | `$validate-code` |      |
| `validation-fragment-codeableconcept-bad-code` | `$validate-code` |      |

### Big

| Name                         | Operation        | Pass |
|:-----------------------------|:-----------------|:-----|
| `big-echo-no-limit`          | `$expand`        |      |
| `big-echo-zero-fifty-limit`  | `$expand`        |      |
| `big-echo-fifty-fifty-limit` | `$expand`        |      |
| `big-circle-bang`            | `$expand`        |      |
| `big-circle-validate`        | `$validate-code` |      |

### Other

| Name                         | Operation        | Pass |
|:-----------------------------|:-----------------|:-----|
| `dual-filter`                | `$expand`        |      |
| `validation-dual-filter-in`  | `$validate-code` |      |
| `validation-dual-filter-out` | `$validate-code` |      |

### Errors

| Name                      | Operation        | Pass |
|:--------------------------|:-----------------|:-----|
| `unknown-system1`         | `$validate-code` |      |
| `unknown-system2`         | `$validate-code` |      |
| `broken-filter-validate`  | `$validate-code` |      |
| `broken-filter2-validate` | `$validate-code` |      |
| `broken-filter-expand`    | `$expand`        |      |
| `combination-ok`          | `$validate-code` |      |
| `combination-bad`         | `$validate-code` |      |

### Deprecated

| Name                     | Operation        | Pass |
|:-------------------------|:-----------------|:-----|
| `withdrawn`              | `$expand`        |      |
| `not-withdrawn`          | `$expand`        |      |
| `withdrawn-validate`     | `$validate-code` |      |
| `not-withdrawn-validate` | `$validate-code` |      |
| `experimental`           | `$expand`        |      |
| `experimental-validate`  | `$validate-code` |      |
| `draft`                  | `$expand`        |      |
| `draft-validate`         | `$validate-code` |      |
| `vs-deprecation`         | `$expand`        |      |
| `deprecating-validate`   | `$validate-code` |      |
| `deprecating-validate-2` | `$validate-code` |      |

### NotSelectable

| Name                                 | Operation        | Pass |
|:-------------------------------------|:-----------------|:-----|
| `notSelectable-prop-all`             | `$expand`        |      |
| `notSelectable-noprop-all`           | `$expand`        |      |
| `notSelectable-reprop-all`           | `$expand`        |      |
| `notSelectable-unprop-all`           | `$expand`        |      |
| `notSelectable-prop-true`            | `$expand`        |      |
| `notSelectable-prop-trueUC`          | `$expand`        |      |
| `notSelectable-noprop-true`          | `$expand`        |      |
| `notSelectable-reprop-true`          | `$expand`        |      |
| `notSelectable-unprop-true`          | `$expand`        |      |
| `notSelectable-prop-false`           | `$expand`        |      |
| `notSelectable-noprop-false`         | `$expand`        |      |
| `notSelectable-reprop-false`         | `$expand`        |      |
| `notSelectable-unprop-false`         | `$expand`        |      |
| `notSelectable-prop-in`              | `$expand`        |      |
| `notSelectable-prop-out`             | `$expand`        |      |
| `notSelectable-prop-true-true`       | `$validate-code` |      |
| `notSelectable-prop-trueUC-true`     | `$validate-code` |      |
| `notSelectable-prop-in-true`         | `$validate-code` |      |
| `notSelectable-prop-out-true`        | `$validate-code` |      |
| `notSelectable-noprop-true-true`     | `$validate-code` |      |
| `notSelectable-reprop-true-true`     | `$validate-code` |      |
| `notSelectable-unprop-true-true`     | `$validate-code` |      |
| `notSelectable-prop-true-false`      | `$validate-code` |      |
| `notSelectable-prop-in-false`        | `$validate-code` |      |
| `notSelectable-prop-in-unknown`      | `$validate-code` |      |
| `notSelectable-prop-out-unknown`     | `$validate-code` |      |
| `notSelectable-prop-out-false`       | `$validate-code` |      |
| `notSelectable-noprop-true-false`    | `$validate-code` |      |
| `notSelectable-reprop-true-false`    | `$validate-code` |      |
| `notSelectable-unprop-true-false`    | `$validate-code` |      |
| `notSelectable-prop-false-true`      | `$validate-code` |      |
| `notSelectable-noprop-false-true`    | `$validate-code` |      |
| `notSelectable-reprop-false-true`    | `$validate-code` |      |
| `notSelectable-unprop-false-true`    | `$validate-code` |      |
| `notSelectable-prop-false-false`     | `$validate-code` |      |
| `notSelectable-noprop-false-false`   | `$validate-code` |      |
| `notSelectable-reprop-false-false`   | `$validate-code` |      |
| `notSelectable-unprop-false-false`   | `$validate-code` |      |
| `notSelectable-noprop-true-unknown`  | `$validate-code` |      |
| `notSelectable-reprop-true-unknown`  | `$validate-code` |      |
| `notSelectable-unprop-true-unknown`  | `$validate-code` |      |
| `notSelectable-prop-true-unknown`    | `$validate-code` |      |
| `notSelectable-prop-false-unknown`   | `$validate-code` |      |
| `notSelectable-noprop-false-unknown` | `$validate-code` |      |
| `notSelectable-reprop-false-unknown` | `$validate-code` |      |
| `notSelectable-unprop-false-unknown` | `$validate-code` |      |

### Inactive

| Name                       | Operation        | Pass |
|:---------------------------|:-----------------|:-----|
| `inactive-expand`          | `$expand`        |      |
| `inactive-inactive-expand` | `$expand`        |      |
| `inactive-active-expand`   | `$expand`        |      |
| `inactive-1-validate`      | `$validate-code` |      |
| `inactive-2-validate`      | `$validate-code` |      |
| `inactive-3-validate`      | `$validate-code` |      |
| `inactive-1a-validate`     | `$validate-code` |      |
| `inactive-2a-validate`     | `$validate-code` |      |
| `inactive-3a-validate`     | `$validate-code` |      |
| `inactive-1b-validate`     | `$validate-code` |      |
| `inactive-2b-validate`     | `$validate-code` |      |
| `inactive-3b-validate`     | `$validate-code` |      |

### Case

| Name                       | Operation        | Pass |
|:---------------------------|:-----------------|:-----|
| `case-insensitive-code1-1` | `$validate-code` |      |
| `case-insensitive-code1-2` | `$validate-code` |      |
| `case-insensitive-code1-3` | `$validate-code` |      |
| `case-sensitive-code1-1`   | `$validate-code` |      |
| `case-sensitive-code1-2`   | `$validate-code` |      |
| `case-sensitive-code1-3`   | `$validate-code` |      |

### Translate

| Name          | Operation    | Pass |
|:--------------|:-------------|:-----|
| `translate-1` | `$translate` |      |

### Tho

| Name                   | Operation | Pass |
|:-----------------------|:----------|:-----|
| `act-class`            | `$expand` |      |
| `act-class-activeonly` | `$expand` |      |

### Exclude

| Name        | Operation | Pass |
|:------------|:----------|:-----|
| `exclude-1` | `$expand` |      |

### Default-valueset-version

| Name                                    | Operation        | Pass |
|:----------------------------------------|:-----------------|:-----|
| `direct-expand-one`                     | `$expand`        |      |
| `direct-expand-two`                     | `$expand`        |      |
| `indirect-expand-one`                   | `$expand`        |      |
| `indirect-expand-two`                   | `$expand`        |      |
| `indirect-expand-zero`                  | `$expand`        |      |
| `indirect-expand-zero-pinned`           | `$expand`        |      |
| `indirect-expand-zero-pinned-wrong`     | `$expand`        |      |
| `indirect-validation-one`               | `$validate-code` |      |
| `indirect-validation-two`               | `$validate-code` |      |
| `indirect-validation-zero`              | `$validate-code` |      |
| `indirect-validation-zero-pinned`       | `$validate-code` |      |
| `indirect-validation-zero-pinned-wrong` | `$validate-code` |      |
