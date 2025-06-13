data/value-set-validate-code-snomed-ct-diagnoses.csv: value-set-validate-code.js
	TEST_NAME="snomed-ct-diagnoses" VALUE_SET="https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/ValueSet/diagnoses-sct" k6 run value-set-validate-code.js

run-read-single-patient:
	TZ=UTC k6 run --out json=/tmp/test-results.json --no-summary read-single-patient.js

run-patient-everything:
	TZ=UTC k6 run --out json=/tmp/test-results.json --no-summary patient-everything.js

run-patient-graph:
	TZ=UTC k6 run --out json=/tmp/test-results.json --no-summary patient-graph.js

report:
	./report.sh /tmp/test-results.json

.PHONY: fmt lint test-coverage cloc-prod cloc-test clean run-read-single-patient run-patient-everything run-patient-graph
