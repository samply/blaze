kds-testdata-2024.0.1.zip:
	wget https://github.com/medizininformatik-initiative/mii-testdata/releases/download/v1.0.1/kds-testdata-2024.0.1.zip

kds-testdata: kds-testdata-2024.0.1.zip
	unzip kds-testdata-2024.0.1.zip

validator_cli.jar:
	wget -q https://github.com/hapifhir/org.hl7.fhir.validator-wrapper/releases/latest/download/validator_cli.jar

validate: validator_cli.jar
	validate.sh

clean:
	rm -r kds-testdata-2024.0.1
	rm kds-testdata-2024.0.1.zip

.PHONY: kds-testdata validate clean
