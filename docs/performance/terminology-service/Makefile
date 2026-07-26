data/value-set-validate-code-snomed-ct-diagnoses.csv: value-set-validate-code.js
	TEST_NAME="snomed-ct-diagnoses" VALUE_SET="https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/ValueSet/diagnoses-sct" k6 run value-set-validate-code.js

data/value-set-validate-code-snomed-ct-body-site.csv: value-set-validate-code.js
	TEST_NAME="snomed-ct-body-site" VALUE_SET="http://hl7.org/fhir/ValueSet/body-site" k6 run value-set-validate-code.js

data/value-set-validate-code-laboratory-observation.csv: value-set-validate-code.js
	TEST_NAME="laboratory-observation" VALUE_SET="http://hl7.org/fhir/uv/ips/ValueSet/results-laboratory-observations-uv-ips" k6 run value-set-validate-code.js

data/value-set-validate-code-icd-10-gm.csv: value-set-validate-code.js
	TEST_NAME="icd-10-gm" VALUE_SET="http://fhir.de/ValueSet/bfarm/icd-10-gm" k6 run value-set-validate-code.js

data/value-set-validate-code-alpha-id.csv: value-set-validate-code.js
	TEST_NAME="alpha-id" VALUE_SET="http://fhir.de/ValueSet/bfarm/alpha-id" k6 run value-set-validate-code.js

data/value-set-validate-code-atc.csv: value-set-validate-code.js
	TEST_NAME="atc" VALUE_SET="http://fhir.de/ValueSet/bfarm/atc" k6 run value-set-validate-code.js

all: data/value-set-validate-code-snomed-ct-diagnoses.csv data/value-set-validate-code-snomed-ct-body-site.csv data/value-set-validate-code-laboratory-observation.csv data/value-set-validate-code-icd-10-gm.csv data/value-set-validate-code-alpha-id.csv data/value-set-validate-code-atc.csv

.PHONY: all
