data/value-set-validate-code-snomed-ct-diagnoses.csv:
	TEST_NAME="snomed-ct-diagnoses" VALUE_SET="https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/ValueSet/diagnoses-sct" k6 run value-set-validate-code.js

plots/value-set-validate-code-snomed-ct-diagnoses.png:
	gnuplot value-set-validate-code-snomed-ct-diagnoses.gnuplot

data/value-set-validate-code-snomed-ct-body-site.csv:
	TEST_NAME="snomed-ct-body-site" VALUE_SET="http://hl7.org/fhir/ValueSet/body-site" k6 run value-set-validate-code.js

plots/value-set-validate-code-snomed-ct-body-site.png: data/value-set-validate-code-snomed-ct-body-site.csv value-set-validate-code-snomed-ct-body-site.gnuplot
	gnuplot value-set-validate-code-snomed-ct-body-site.gnuplot

data/value-set-validate-code-laboratory-observation.csv: value-set-validate-code.js
	TEST_NAME="laboratory-observation" VALUE_SET="http://hl7.org/fhir/uv/ips/ValueSet/results-laboratory-observations-uv-ips" k6 run value-set-validate-code.js

plots/value-set-validate-code-laboratory-observation.png: data/value-set-validate-code-laboratory-observation.csv value-set-validate-code-laboratory-observation.gnuplot
	gnuplot value-set-validate-code-laboratory-observation.gnuplot

data/value-set-validate-code-icd-10-gm.csv:
	TEST_NAME="icd-10-gm" VALUE_SET="http://fhir.de/ValueSet/bfarm/icd-10-gm" k6 run value-set-validate-code.js

plots/value-set-validate-code-icd-10-gm.png:
	gnuplot value-set-validate-code-icd-10-gm.gnuplot

data/value-set-validate-code-alpha-id.csv: value-set-validate-code.js
	TEST_NAME="alpha-id" VALUE_SET="http://fhir.de/ValueSet/bfarm/alpha-id" k6 run value-set-validate-code.js

plots/value-set-validate-code-alpha-id.png: data/value-set-validate-code-alpha-id.csv value-set-validate-code-alpha-id.gnuplot
	gnuplot value-set-validate-code-alpha-id.gnuplot

data/value-set-validate-code-atc.csv:
	TEST_NAME="atc" VALUE_SET="http://fhir.de/ValueSet/bfarm/atc" k6 run value-set-validate-code.js

plots/value-set-validate-code-atc.png:
	gnuplot value-set-validate-code-atc.gnuplot
