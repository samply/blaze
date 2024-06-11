build:
	sushi build -s .

validator_cli.jar:
	wget -q https://github.com/hapifhir/org.hl7.fhir.validator-wrapper/releases/latest/download/validator_cli.jar

validate: validator_cli.jar
	java -jar validator_cli.jar -version 4.0.1 -level error -debug -ig fsh-generated/resources fsh-generated/resources/Task-*.json fsh-generated/resources/Bundle-*.json

.PHONY: build validate
