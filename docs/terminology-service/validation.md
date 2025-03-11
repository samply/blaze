# Validation

## Download the FHIR Validator

The FHIR validator can be downloaded from its [GitHub Release Page][1].

## Usage

In case Blaze runs with enabled terminology service under `http://localhost:8080/fhir`, the following command will validate a potential Condition resource against the implementation guide `de.medizininformatikinitiative.kerndatensatz.diagnose#2025.0.0`.  The important parameter is `-tx` with the URL of Blaze. Blaze will be used by the validator to validate all ValueSet bindings and potential errors will be reported by the validator.

```sh
java -jar validator_cli.jar -version 4.0.1 -level error \
  -tx http://localhost:8080/fhir -authorise-non-conformant-tx-servers \
  -ig de.medizininformatikinitiative.kerndatensatz.diagnose#2025.0.0 \
  "Condition-1.json"
```

> [!NOTE]
> The -authorise-non-conformant-tx-servers switch is necessary since v6.5.21 of the validator because Blaze currently doesn't pass all test cases defined in the [FHIR Terminology Ecosystem IG][2]. The process can be followed in the issue [#2635](https://github.com/samply/blaze/issues/2635).

[1]: <https://github.com/hapifhir/org.hl7.fhir.core/releases/latest>
[2]: <https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/testcases.html>
