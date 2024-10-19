run-read-single-patient:
	TZ=UTC k6 run --out json=/tmp/test-results.json --no-summary read-single-patient.js

run-patient-everything:
	TZ=UTC k6 run --out json=/tmp/test-results.json --no-summary patient-everything.js

report:
	./report.sh /tmp/test-results.json

.PHONY: fmt lint test-coverage cloc-prod cloc-test run-read-single-patient run-patient-everything
