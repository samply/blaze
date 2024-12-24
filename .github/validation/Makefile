kds-testdata-2024.0.1.zip:
	wget -q https://github.com/medizininformatik-initiative/mii-testdata/releases/download/v1.0.1/kds-testdata-2024.0.1.zip

kds-test-data: kds-testdata-2024.0.1.zip
	unzip kds-testdata-2024.0.1.zip
	rm kds-testdata-2024.0.1.zip

validator_cli.jar:
	wget -q https://github.com/hapifhir/org.hl7.fhir.validator-wrapper/releases/latest/download/validator_cli.jar

validate-medication: validator_cli.jar
	./validate-medication.sh

clean:
	rm -rf kds-testdata-2024.0.1
	rm -f validator_cli.jar

.PHONY: kds-test-data validate clean
